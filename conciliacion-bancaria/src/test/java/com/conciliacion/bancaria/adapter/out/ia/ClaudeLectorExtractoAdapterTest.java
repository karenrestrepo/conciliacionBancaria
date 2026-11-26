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

    @Test
    @DisplayName("un extracto de varias páginas se lee por bloques y se une en orden")
    void variasPaginas() throws Exception {
        String primero = """
                {"tipoProducto":"CUENTA","ultimosDigitosTarjeta":null,"saldoAnterior":100,"saldoFinal":null,
                 "totalCreditos":null,"totalDebitos":null,
                 "movimientos":[{"fecha":"2026-12-01","descripcion":"UNO","monto":10,"tipo":"CREDITO"}]}""";
        String segundo = """
                {"tipoProducto":"CUENTA","ultimosDigitosTarjeta":null,"saldoAnterior":null,"saldoFinal":130,
                 "totalCreditos":null,"totalDebitos":null,
                 "movimientos":[{"fecha":"2026-12-02","descripcion":"DOS","monto":20,"tipo":"CREDITO"}]}""";
        servidor.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                .andRespond(withSuccess(respuesta("end_turn", primero), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                .andRespond(withSuccess(respuesta("end_turn", segundo), MediaType.APPLICATION_JSON));

        LecturaExtracto lectura = adapter.leer(pdf(5), "2026-12");

        assertThat(lectura.getMovimientos()).extracting(LecturaExtracto.MovimientoLeido::getDescripcion)
                .containsExactly("UNO", "DOS");
        assertThat(lectura.getSaldoAnterior()).isEqualByComparingTo("100");
        assertThat(lectura.getSaldoFinal()).isEqualByComparingTo("130");
        servidor.verify();
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
