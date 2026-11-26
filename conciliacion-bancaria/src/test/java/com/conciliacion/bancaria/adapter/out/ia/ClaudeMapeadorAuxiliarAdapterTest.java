package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.model.MapeoColumnas;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("ClaudeMapeadorAuxiliarAdapter — con respuestas simuladas de la API")
class ClaudeMapeadorAuxiliarAdapterTest {

    private static final String URL = "https://api.ejemplo.test/v1/messages";

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.ejemplo.test");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ClaudeMapeadorAuxiliarAdapter adapter = new ClaudeMapeadorAuxiliarAdapter(
            builder.build(), mapper, "clave-de-prueba", "modelo-prueba", "modelo-respaldo");

    private String respuesta() throws Exception {
        String mapeo = """
                {"hoja":0,"primeraFilaDatos":17,"columnaFecha":0,"columnaComprobante":1,"columnasDescripcion":[4],
                 "esquemaMonto":"DEBITO_CREDITO","columnaDebito":9,"columnaCredito":10,"columnaMonto":-1,
                 "columnaNaturaleza":-1,"valoresDebito":[],"positivoEsDebito":true,"separadorDecimal":",",
                 "ordenFecha":"DMA","saldoInicial":null,"saldoFinal":null,
                 "totalDebitos":{"fila":30,"columna":9},"totalCreditos":{"fila":30,"columna":10}}""";
        return mapper.writeValueAsString(Map.of(
                "stop_reason", "end_turn",
                "content", List.of(Map.of("type", "text", "text", mapeo))));
    }

    @Test
    @DisplayName("envía solo la muestra y convierte la respuesta en un mapeo")
    void mapea() throws Exception {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[0].content[0].text").value("Muestra del archivo:\nF0 | c0=1"))
                .andRespond(withSuccess(respuesta(), MediaType.APPLICATION_JSON));

        MapeoColumnas mapeo = adapter.mapear("F0 | c0=1");

        assertThat(mapeo.getColumnaDebito()).isEqualTo(9);
        assertThat(mapeo.getTotalDebitos().getFila()).isEqualTo(30);
        servidor.verify();
    }

    @Test
    @DisplayName("ante el límite de velocidad espera y reintenta")
    void limiteDeVelocidad() throws Exception {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.add("retry-after", "0");
        servidor.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(cabeceras));
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(respuesta(), MediaType.APPLICATION_JSON));

        assertThat(adapter.mapear("x").getPrimeraFilaDatos()).isEqualTo(17);
        servidor.verify();
    }

    @Test
    @DisplayName("falla de la herramienta: error del servicio se traduce a un mensaje claro")
    void servicioCaido() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> adapter.mapear("x")).hasMessageContaining("no está disponible");
    }
}
