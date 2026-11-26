package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Llamada a la API de Claude con respuesta en un esquema JSON fijo. */
@Slf4j
class ClaudeCliente {

    private static final int REINTENTOS_POR_LIMITE = 2;
    private static final long ESPERA_POR_DEFECTO_SEGUNDOS = 15;
    private static final long ESPERA_MAXIMA_SEGUNDOS = 60;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    ClaudeCliente(RestClient restClient, ObjectMapper objectMapper, String apiKey) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    void exigirClave() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new CsvValidationException(
                    "No está configurada la clave de la API (variable de entorno ANTHROPIC_API_KEY)");
        }
    }

    <T> T pedir(String modelo, String instrucciones, List<Map<String, Object>> contenido,
                Map<String, Object> esquema, int maxTokens, Class<T> tipo, String mensajeSiSeCorta) {
        exigirClave();
        Map<String, Object> solicitud = Map.of(
                "model", modelo,
                "max_tokens", maxTokens,
                "system", instrucciones,
                "messages", List.of(Map.of("role", "user", "content", contenido)),
                "output_config", Map.of("format", Map.of("type", "json_schema", "schema", esquema)));
        return interpretar(enviar(solicitud), modelo, tipo, mensajeSiSeCorta);
    }

    private JsonNode enviar(Map<String, Object> solicitud) {
        for (int intento = 0; ; intento++) {
            try {
                return restClient.post()
                        .uri("/v1/messages")
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(solicitud)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException e) {
                int estado = e.getStatusCode().value();
                log.warn("La API de IA respondió con error HTTP {}", estado);
                // Límite de velocidad o servicio saturado: suele resolverse esperando un poco.
                if ((estado == 429 || estado == 529) && intento < REINTENTOS_POR_LIMITE) {
                    String indicada = e.getResponseHeaders() == null
                            ? null : e.getResponseHeaders().getFirst("retry-after");
                    esperar(indicada);
                    continue;
                }
                throw new CsvValidationException(mensajeError(estado));
            } catch (RestClientException e) {
                log.warn("No se pudo obtener respuesta de la API de IA: {}", e.getMessage());
                throw new CsvValidationException(
                        "No se pudo comunicar con el servicio de IA. Intente de nuevo más tarde");
            }
        }
    }

    private void esperar(String segundosIndicados) {
        long segundos = ESPERA_POR_DEFECTO_SEGUNDOS;
        if (segundosIndicados != null && segundosIndicados.matches("\\d{1,4}")) {
            segundos = Long.parseLong(segundosIndicados);
        }
        try {
            Thread.sleep(Math.min(segundos, ESPERA_MAXIMA_SEGUNDOS) * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CsvValidationException("Se interrumpió la lectura");
        }
    }

    private <T> T interpretar(JsonNode respuesta, String modelo, Class<T> tipo, String mensajeSiSeCorta) {
        if (respuesta == null) {
            throw new CsvValidationException("El servicio de IA no devolvió respuesta");
        }
        // Consumo de cada lectura, para controlar el gasto.
        log.info("Lectura con IA modelo={} tokensEntrada={} tokensSalida={}", modelo,
                respuesta.path("usage").path("input_tokens").asLong(),
                respuesta.path("usage").path("output_tokens").asLong());

        String motivo = respuesta.path("stop_reason").asText();
        if ("max_tokens".equals(motivo)) {
            throw new CsvValidationException(mensajeSiSeCorta);
        }
        if (!"end_turn".equals(motivo)) {
            throw new CsvValidationException("El servicio de IA no pudo leer este documento");
        }

        for (JsonNode bloque : respuesta.path("content")) {
            if ("text".equals(bloque.path("type").asText())) {
                try {
                    return objectMapper.readValue(bloque.path("text").asText(), tipo);
                } catch (JsonProcessingException e) {
                    break;
                }
            }
        }
        throw new CsvValidationException("La respuesta de la IA no tiene el formato esperado");
    }

    private String mensajeError(int estado) {
        return switch (estado) {
            case 401, 403 -> "La clave de la API no es válida o no tiene permiso";
            case 413 -> "El archivo es demasiado grande para el servicio de IA";
            case 429 -> "Se alcanzó el límite de uso de la API. Revise el saldo y los límites";
            case 400 -> "El servicio de IA rechazó la solicitud. Verifique que el archivo no tenga contraseña "
                    + "y que la cuenta tenga saldo";
            default -> "El servicio de IA no está disponible (error " + estado + "). Intente más tarde";
        };
    }

    /** Objeto JSON con todas sus propiedades obligatorias, como exigen las salidas estructuradas. */
    static Map<String, Object> objeto(LinkedHashMap<String, Object> propiedades) {
        return Map.of(
                "type", "object",
                "properties", propiedades,
                "required", new ArrayList<>(propiedades.keySet()),
                "additionalProperties", false);
    }
}
