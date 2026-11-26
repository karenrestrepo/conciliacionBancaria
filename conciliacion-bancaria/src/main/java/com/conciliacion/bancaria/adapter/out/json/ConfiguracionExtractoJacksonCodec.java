package com.conciliacion.bancaria.adapter.out.json;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfiguracionExtractoJacksonCodec implements ConfiguracionExtractoCodec {

    private final ObjectMapper objectMapper;

    @Override
    public ConfiguracionExtractoDetalle leer(String json) {
        if (json == null || json.isBlank()) {
            throw new CsvValidationException(
                    "La configuración de extracto no tiene detalle definido.");
        }
        try {
            return objectMapper.readValue(json, ConfiguracionExtractoDetalle.class);
        } catch (Exception e) {
            throw new CsvValidationException(
                    "La configuración de extracto guardada es inválida: " + e.getMessage());
        }
    }

    @Override
    public String escribir(ConfiguracionExtractoDetalle detalle) {
        try {
            return objectMapper.writeValueAsString(detalle);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Error al serializar la configuración de extracto: " + e.getMessage(), e);
        }
    }
}
