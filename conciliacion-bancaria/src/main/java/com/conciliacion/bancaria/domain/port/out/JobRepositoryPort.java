package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.port.in.JobStatusUseCase.JobStatus;

public interface JobRepositoryPort {

    String crearJob(Long idConciliacion);

    void actualizarProgreso(String jobId, int progreso);

    void completar(String jobId);

    void fallar(String jobId, String mensajeError);

    JobStatus obtener(String jobId);
}
