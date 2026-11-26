package com.conciliacion.bancaria.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

// Puerto para los 9 eventos obligatorios de observabilidad (TRD §7.5) + eventos adicionales
public interface EventLogPort {

    void csvUpload(Long idConciliacion, Long idUsuario, String nombreArchivo);

    void csvValidationFailed(Long idConciliacion, String motivo);

    void initConciliacion(Long idConciliacion, Long idUsuario);

    void engineCompleted(Long idConciliacion, long durationMs, int nTotal);

    void openReview(Long idConciliacion);

    void actionAccept(Long idConciliacion, Long idSugerencia, Long idUsuario);

    void actionReject(Long idConciliacion, Long idSugerencia, Long idUsuario);

    void closeConciliacion(Long idConciliacion, Long idUsuario);

    void integrationError(Long idConciliacion, String error);

    /**
     * Un contable que existía en una recarga anterior del auxiliar desapareció del
     * archivo nuevo y tenía una sugerencia activa — se revirtió (bancario a PENDIENTE)
     * y se eliminó el contable. Lleva snapshot completo de la fila borrada porque,
     * a diferencia de otros eventos, el registro deja de existir después de este log.
     */
    void contableRevertido(Long idConciliacion, Long idMovContable, LocalDate fechaContable,
                           BigDecimal montoContable, String tipoContable, String descripcionContable,
                           Long idMovBancario, String estadoSugerenciaAnterior);
}