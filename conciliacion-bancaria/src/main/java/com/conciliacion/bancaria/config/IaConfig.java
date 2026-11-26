package com.conciliacion.bancaria.config;

import com.conciliacion.bancaria.adapter.out.ia.ClaudeLectorDocumentoAuxiliarAdapter;
import com.conciliacion.bancaria.adapter.out.ia.ClaudeLectorExtractoAdapter;
import com.conciliacion.bancaria.adapter.out.ia.ClaudeMapeadorAuxiliarAdapter;
import com.conciliacion.bancaria.adapter.out.ia.ClaudeMapeadorExtractoAdapter;
import com.conciliacion.bancaria.adapter.out.ia.LectorDocumentoSimuladoAdapter;
import com.conciliacion.bancaria.adapter.out.ia.LectorSimuladoAdapter;
import com.conciliacion.bancaria.adapter.out.ia.MapeadorExtractoSimuladoAdapter;
import com.conciliacion.bancaria.adapter.out.ia.MapeadorSimuladoAdapter;
import com.conciliacion.bancaria.domain.port.out.LectorDocumentoAuxiliarIaPort;
import com.conciliacion.bancaria.domain.port.out.LectorExtractoIaPort;
import com.conciliacion.bancaria.domain.port.out.MapeadorAuxiliarIaPort;
import com.conciliacion.bancaria.domain.port.out.MapeadorExtractoIaPort;
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
            @Value("${app.ia.modelo-respaldo:claude-sonnet-5-5}") String modeloRespaldo,
            @Value("${app.ia.url:https://api.anthropic.com}") String url,
            ObjectMapper objectMapper) {
        return switch (proveedor) {
            case "simulado" -> new LectorSimuladoAdapter();
            case "claude" -> new ClaudeLectorExtractoAdapter(
                    clienteHttp(url), objectMapper, apiKey, modelo, modeloRespaldo);
            default -> throw proveedorDesconocido(proveedor);
        };
    }

    @Bean
    public MapeadorAuxiliarIaPort mapeadorAuxiliarIa(
            @Value("${app.ia.proveedor:simulado}") String proveedor,
            @Value("${app.ia.api-key:}") String apiKey,
            @Value("${app.ia.modelo:claude-haiku-5-5}") String modelo,
            @Value("${app.ia.modelo-respaldo:claude-sonnet-5-5}") String modeloRespaldo,
            @Value("${app.ia.url:https://api.anthropic.com}") String url,
            ObjectMapper objectMapper) {
        return switch (proveedor) {
            case "simulado" -> new MapeadorSimuladoAdapter();
            case "claude" -> new ClaudeMapeadorAuxiliarAdapter(
                    clienteHttp(url), objectMapper, apiKey, modelo, modeloRespaldo);
            default -> throw proveedorDesconocido(proveedor);
        };
    }

    @Bean
    public LectorDocumentoAuxiliarIaPort lectorDocumentoAuxiliarIa(
            @Value("${app.ia.proveedor:simulado}") String proveedor,
            @Value("${app.ia.api-key:}") String apiKey,
            @Value("${app.ia.modelo:claude-haiku-5-5}") String modelo,
            @Value("${app.ia.modelo-respaldo:claude-sonnet-5-5}") String modeloRespaldo,
            @Value("${app.ia.url:https://api.anthropic.com}") String url,
            ObjectMapper objectMapper) {
        return switch (proveedor) {
            case "simulado" -> new LectorDocumentoSimuladoAdapter();
            case "claude" -> new ClaudeLectorDocumentoAuxiliarAdapter(
                    clienteHttp(url), objectMapper, apiKey, modelo, modeloRespaldo);
            default -> throw proveedorDesconocido(proveedor);
        };
    }

    @Bean
    public MapeadorExtractoIaPort mapeadorExtractoIa(
            @Value("${app.ia.proveedor:simulado}") String proveedor,
            @Value("${app.ia.api-key:}") String apiKey,
            @Value("${app.ia.modelo:claude-haiku-5-5}") String modelo,
            @Value("${app.ia.modelo-respaldo:claude-sonnet-5-5}") String modeloRespaldo,
            @Value("${app.ia.url:https://api.anthropic.com}") String url,
            ObjectMapper objectMapper) {
        return switch (proveedor) {
            case "simulado" -> new MapeadorExtractoSimuladoAdapter();
            case "claude" -> new ClaudeMapeadorExtractoAdapter(
                    clienteHttp(url), objectMapper, apiKey, modelo, modeloRespaldo);
            default -> throw proveedorDesconocido(proveedor);
        };
    }

    private RestClient clienteHttp(String url) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(10));
        fabrica.setReadTimeout(Duration.ofMinutes(5));
        return RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    private IllegalStateException proveedorDesconocido(String proveedor) {
        return new IllegalStateException(
                "app.ia.proveedor debe ser 'simulado' o 'claude', no '" + proveedor + "'");
    }
}
