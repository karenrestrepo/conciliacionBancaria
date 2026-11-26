package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.model.ArchivoLeido.Documento;
import com.conciliacion.bancaria.domain.model.ArchivoLeido.Tipo;
import com.conciliacion.bancaria.domain.model.AuxiliarLeido;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ClaudeLectorDocumentoAuxiliarAdapter — con respuestas simuladas de la API")
class ClaudeLectorDocumentoAuxiliarAdapterTest {

    private static final String URL = "https://api.ejemplo.test/v1/messages";

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.ejemplo.test");
    private final MockRestServiceServer servidor =
            MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    private final ClaudeLectorDocumentoAuxiliarAdapter adapter = new ClaudeLectorDocumentoAuxiliarAdapter(
            builder.build(), mapper, "clave-de-prueba", "modelo-prueba", "");

    private String respuesta(String descripcion, String saldoInicial, String saldoFinal) throws Exception {
        String json = """
                {"saldoInicial":%s,"saldoFinal":%s,"totalDebitos":null,"totalCreditos":null,
                 "movimientos":[{"fecha":"2026-12-01","comprobante":null,"descripcion":"%s",
                 "debito":10,"credito":0}]}""".formatted(saldoInicial, saldoFinal, descripcion);
        return mapper.writeValueAsString(Map.of(
                "stop_reason", "end_turn",
                "content", List.of(Map.of("type", "text", "text", json))));
    }

    @Test
    @DisplayName("un reporte de texto largo se lee por bloques y se une en orden")
    void textoPorBloques() throws Exception {
        String texto = IntStream.rangeClosed(1, 130).mapToObj(i -> "linea " + i)
                .collect(Collectors.joining("\n"));
        servidor.expect(requestTo(URL))
                .andExpect(content().string(Matchers.containsString("líneas 1 a 120 de 130")))
                .andRespond(withSuccess(respuesta("UNO", "100", "null"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL))
                .andExpect(content().string(Matchers.containsString("líneas 121 a 130 de 130")))
                .andRespond(withSuccess(respuesta("DOS", "null", "120"), MediaType.APPLICATION_JSON));

        AuxiliarLeido leido = adapter.leer(
                new Documento(Tipo.TEXTO, texto.getBytes(StandardCharsets.UTF_8)), "2026-12");

        assertThat(leido.getMovimientos()).extracting(AuxiliarLeido.Fila::getDescripcion)
                .containsExactly("UNO", "DOS");
        assertThat(leido.getSaldoInicial()).isEqualByComparingTo("100");
        assertThat(leido.getSaldoFinal()).isEqualByComparingTo("120");
        servidor.verify();
    }

    @Test
    @DisplayName("una foto se envía como imagen")
    void foto() throws Exception {
        servidor.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("image"))
                .andExpect(jsonPath("$.messages[0].content[0].source.media_type").value("image/png"))
                .andRespond(withSuccess(respuesta("UNO", "null", "null"), MediaType.APPLICATION_JSON));

        assertThat(adapter.leer(new Documento(Tipo.PNG, new byte[]{1}), "2026-12").getMovimientos()).hasSize(1);
        servidor.verify();
    }
}
