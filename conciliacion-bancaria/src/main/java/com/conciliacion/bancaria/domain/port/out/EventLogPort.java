package com.conciliacion.bancaria.domain.port.out;

// Puerto para los 9 eventos obligatorios de observabilidad (TRD §7.5)
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
}