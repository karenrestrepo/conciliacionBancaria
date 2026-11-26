package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.model.MapeoExtracto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ClaudeMapeadorExtractoAdapter — con respuestas simuladas de la API")
class ClaudeMapeadorExtractoAdapterTest {

    @Test
    @DisplayName("convierte la respuesta en un mapeo de extracto")
    void mapea() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.ejemplo.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        String mapeo = """
                {"tipoProducto":"CUENTA","ultimosDigitosTarjeta":null,"hoja":0,"primeraFilaDatos":1,
                 "columnaFecha":0,"columnasDescripcion":[1],"esquemaMonto":"ABONO_CARGO","columnaAbonos":2,
                 "columnaCargos":3,"columnaMonto":-1,"columnaNaturaleza":-1,"valoresAbono":[],
                 "positivoEsAbono":true,"separadorDecimal":".","ordenFecha":"DMA","saldoAnterior":null,
                 "saldoFinal":{"fila":9,"columna":2},"totalAbonos":null,"totalCargos":null}""";
        servidor.expect(requestTo("https://api.ejemplo.test/v1/messages"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("text"))
                .andRespond(withSuccess(mapper.writeValueAsString(Map.of(
                        "stop_reason", "end_turn",
                        "content", List.of(Map.of("type", "text", "text", mapeo)))), MediaType.APPLICATION_JSON));

        MapeoExtracto leido = new ClaudeMapeadorExtractoAdapter(
                builder.build(), mapper, "clave-de-prueba", "modelo-prueba", "").mapear("F0", "2026-12");

        assertThat(leido.getColumnaCargos()).isEqualTo(3);
        assertThat(leido.getSaldoFinal().getFila()).isEqualTo(9);
        servidor.verify();
    }
}
