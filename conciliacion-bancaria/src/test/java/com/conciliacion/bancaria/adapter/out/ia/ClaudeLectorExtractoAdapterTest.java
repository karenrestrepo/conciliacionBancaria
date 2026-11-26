package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("ClaudeLectorExtractoAdapter — con respuestas simuladas de la API")
class ClaudeLectorExtractoAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.ejemplo.test");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ClaudeLectorExtractoAdapter adapter =
            new ClaudeLectorExtractoAdapter(builder.build(), mapper, "clave-de-prueba", "modelo-prueba", "modelo-respaldo");

    private static byte[] pdf(int paginas) {
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            for (int i = 0; i < paginas; i++) {
                documento.addPage(new PDPage());
            }
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private String respuesta(String motivo, String texto) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "stop_reason", motivo,
                "content", List.of(Map.of("type", "text", "text", texto))));
    }

    @Test
    @DisplayName("convierte la respuesta en movimientos")
    void respuestaCorrecta() throws Exception {
        String json = """
                {"tipoProducto":"CUENTA","ultimosDigitosTarjeta":null,"saldoAnterior":100,"saldoFinal":150.50,
                 "totalCreditos":null,"totalDebitos":null,
                 "movimientos":[{"fecha":"2026-12-01","descripcion":"ABONO","monto":50.50,"tipo":"CREDITO"}]}""";
        servidor.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                .andExpect(header("x-api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("document"))
                .andRespond(withSuccess(respuesta("end_turn", json), MediaType.APPLICATION_JSON));

        LecturaExtracto lectura = adapter.leer(pdf(1), "2026-12");

        assertThat(lectura.getMovimientos()).hasSize(1);
        assertThat(lectura.getSaldoFinal()).isEqualByComparingTo("150.50");
        servidor.verify();
    }

    @Test
    @DisplayName("falla de la herramienta: error del servicio se traduce a un mensaje claro")
    void servicioCaido() {
        servidor.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.leer(pdf(1), "2026-12"))
                .isInstanceOf(CsvValidationException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    @DisplayName("respuesta cortada por longitud no se acepta")
    void respuestaCortada() throws Exception {
        servidor.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                .andRespond(withSuccess(respuesta("max_tokens", "{"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.leer(pdf(1), "2026-12"))
                .hasMessageContaining("demasiado largo");
    }

    @Test
    @DisplayName("sin clave no se llama a la API")
    void sinClave() {
        var sinClave = new ClaudeLectorExtractoAdapter(builder.build(), mapper, "", "modelo-prueba", "");

        assertThatThrownBy(() -> sinClave.leer(pdf(1), "2026-12"))
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    private static String parte(String descripcion, String saldoAnterior, String saldoFinal) {
        return """
                {"tipoProducto":"CUENTA","ultimosDigitosTarjeta":null,"saldoAnterior":%s,"saldoFinal":%s,
                 "totalCreditos":null,"totalDebitos":null,
                 "movimientos":[{"fecha":"2026-12-01","descripcion":"%s","monto":10,"tipo":"CREDITO"}]}"""
                .formatted(saldoAnterior, saldoFinal, descripcion);
    }

    @Test
    @DisplayName("un extracto de varias páginas se lee por bloques en paralelo y se une en orden")
    void variasPaginas() throws Exception {
        RestClient.Builder propio = RestClient.builder().baseUrl("https://api.ejemplo.test");
        // Los bloques se piden al tiempo: cada respuesta se elige por las páginas pedidas.
        MockRestServiceServer paralelo = MockRestServiceServer.bindTo(propio).ignoreExpectOrder(true).build();
        Map<String, String> porPaginas = Map.of(
                "páginas 1 a 2 de 5", parte("UNO", "100", "null"),
                "páginas 3 a 4 de 5", parte("DOS", "null", "null"),
                "páginas 5 a 5 de 5", parte("TRES", "null", "130"));
        for (var entrada : porPaginas.entrySet()) {
            paralelo.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(entrada.getKey())))
                    .andRespond(withSuccess(respuesta("end_turn", entrada.getValue()), MediaType.APPLICATION_JSON));
        }
        var lector = new ClaudeLectorExtractoAdapter(
                propio.build(), mapper, "clave-de-prueba", "modelo-prueba", "");

        LecturaExtracto lectura = lector.leer(pdf(5), "2026-12");

        assertThat(lectura.getMovimientos()).extracting(LecturaExtracto.MovimientoLeido::getDescripcion)
                .containsExactly("UNO", "DOS", "TRES");
        assertThat(lectura.getSaldoAnterior()).isEqualByComparingTo("100");
        assertThat(lectura.getSaldoFinal()).isEqualByComparingTo("130");
        paralelo.verify();
    }

    @Test
    @DisplayName("el reintento usa el modelo de respaldo")
    void respaldo() throws Exception {
        servidor.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                .andExpect(jsonPath("$.model").value("modelo-respaldo"))
                .andRespond(withSuccess(respuesta("end_turn",
                        "{\"tipoProducto\":\"CUENTA\",\"movimientos\":[]}"), MediaType.APPLICATION_JSON));

        adapter.leerConRespaldo(pdf(1), "2026-12");

        servidor.verify();
    }
}
