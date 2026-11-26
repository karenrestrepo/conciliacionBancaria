package com.conciliacion.bancaria.config;

import com.conciliacion.bancaria.adapter.out.ia.ClaudeLectorExtractoAdapter;
import com.conciliacion.bancaria.adapter.out.ia.LectorSimuladoAdapter;
import com.conciliacion.bancaria.domain.port.out.LectorExtractoIaPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class IaConfig {

    @Bean
    public LectorExtractoIaPort lectorExtractoIa(
            @Value("${app.ia.proveedor:simulado}") String proveedor,
            @Value("${app.ia.api-key:}") String apiKey,
            @Value("${app.ia.modelo:claude-haiku-5-5}") String modelo,
            @Value("${app.ia.url:https://api.anthropic.com}") String url,
            ObjectMapper objectMapper) {
        return switch (proveedor) {
            case "simulado" -> new LectorSimuladoAdapter();
            case "claude" -> {
                var fabrica = new SimpleClientHttpRequestFactory();
                fabrica.setConnectTimeout(Duration.ofSeconds(10));
                fabrica.setReadTimeout(Duration.ofMinutes(5));
                RestClient cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
                yield new ClaudeLectorExtractoAdapter(cliente, objectMapper, apiKey, modelo);
            }
            default -> throw new IllegalStateException(
                    "app.ia.proveedor debe ser 'simulado' o 'claude', no '" + proveedor + "'");
        };
    }
}
