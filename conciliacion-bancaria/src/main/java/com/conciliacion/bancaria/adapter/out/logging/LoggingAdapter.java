package com.conciliacion.bancaria.adapter.out.logging;

import com.conciliacion.bancaria.domain.port.out.EventLogPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingAdapter implements EventLogPort {

    private final ObjectMapper objectMapper;

    // ── 9 eventos obligatorios del TRD §7.5 ──────────────────────────────────

    @Override
    public void csvUpload(Long idConciliacion, Long idUsuario, String nombreArchivo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idUsuario", idUsuario);
        datos.put("archivo", nombreArchivo);
        log("CSV_UPLOAD", idConciliacion, datos);
    }

    @Override
    public void csvValidationFailed(Long idConciliacion, String motivo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("motivo", motivo);
        log("CSV_VALIDATION_FAILED", idConciliacion, datos);
    }

    @Override
    public void initConciliacion(Long idConciliacion, Long idUsuario) {
        log("INIT_CONCILIACION", idConciliacion, Map.of(
                "idUsuario", idUsuario
        ));
    }

    @Override
    public void engineCompleted(Long idConciliacion, long durationMs, int nTotal) {
        log("ENGINE_COMPLETED", idConciliacion, Map.of(
                "durationMs", durationMs,
                "nTotal", nTotal
        ));
    }

    @Override
    public void openReview(Long idConciliacion) {
        log("OPEN_REVIEW", idConciliacion, Map.of());
    }

    @Override
    public void actionAccept(Long idConciliacion, Long idSugerencia, Long idUsuario) {
        log("ACTION_ACCEPT", idConciliacion, Map.of(
                "idSugerencia", idSugerencia,
                "idUsuario", idUsuario
        ));
    }

    @Override
    public void actionReject(Long idConciliacion, Long idSugerencia, Long idUsuario) {
        log("ACTION_REJECT", idConciliacion, Map.of(
                "idSugerencia", idSugerencia,
                "idUsuario", idUsuario
        ));
    }

    @Override
    public void closeConciliacion(Long idConciliacion, Long idUsuario) {
        log("CLOSE_CONCILIACION", idConciliacion, Map.of(
                "idUsuario", idUsuario
        ));
    }

    @Override
    public void integrationError(Long idConciliacion, String error) {
        log("INTEGRATION_ERROR", idConciliacion, Map.of(
                "error", error
        ));
    }

    @Override
    public void contableRevertido(Long idConciliacion, Long idMovContable, java.time.LocalDate fechaContable,
                                  java.math.BigDecimal montoContable, String tipoContable,
                                  String descripcionContable, Long idMovBancario,
                                  String estadoSugerenciaAnterior) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idMovContable", idMovContable);
        datos.put("fechaContable", fechaContable != null ? fechaContable.toString() : null);
        datos.put("montoContable", montoContable);
        datos.put("tipoContable", tipoContable);
        datos.put("descripcionContable", descripcionContable);
        datos.put("idMovBancario", idMovBancario);
        datos.put("estadoSugerenciaAnterior", estadoSugerenciaAnterior);
        log("CONTABLE_REVERTIDO", idConciliacion, datos);
    }

    // ── Utilidad: genera JSON estructurado ───────────────────────────────────

    private void log(String evento, Long idConciliacion, Map<String, Object> datos) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestamp", Instant.now().toString());
            entry.put("evento", evento);
            entry.put("idConciliacion", idConciliacion);
            entry.putAll(datos);
            log.info(objectMapper.writeValueAsString(entry));
        } catch (Exception e) {
            log.error("Error serializando evento de log: {}", e.getMessage());
        }
    }
}