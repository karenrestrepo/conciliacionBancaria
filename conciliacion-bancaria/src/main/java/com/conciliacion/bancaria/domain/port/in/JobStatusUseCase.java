package com.conciliacion.bancaria.domain.port.in;

public interface JobStatusUseCase {

    // Retorna el estado del job para el polling del frontend
    JobStatus obtenerEstado(String jobId);

    record JobStatus(String jobId, String estado, int progreso, String mensajeError) {}
}