package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConciliacionJobEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.JobJpaRepository;
import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.domain.port.in.JobStatusUseCase.JobStatus;
import com.conciliacion.bancaria.domain.port.out.JobRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobRepositoryAdapter implements JobRepositoryPort {

    private final JobJpaRepository jpaRepository;

    @Override
    public String crearJob(Long idConciliacion) {
        String jobId = UUID.randomUUID().toString();
        jpaRepository.save(ConciliacionJobEntity.builder()
                .id(jobId)
                .idConciliacion(idConciliacion)
                .estado("PENDING")
                .progreso(0)
                .build());
        return jobId;
    }

    @Override
    public void actualizarProgreso(String jobId, int progreso) {
        jpaRepository.findById(jobId).ifPresent(job -> {
            job.setEstado("IN_PROGRESS");
            job.setProgreso(progreso);
            jpaRepository.save(job);
        });
    }

    @Override
    public void completar(String jobId) {
        jpaRepository.findById(jobId).ifPresent(job -> {
            job.setEstado("COMPLETED");
            job.setProgreso(100);
            job.setTsFin(LocalDateTime.now());
            jpaRepository.save(job);
        });
    }

    @Override
    public void fallar(String jobId, String mensajeError) {
        jpaRepository.findById(jobId).ifPresent(job -> {
            job.setEstado("FAILED");
            job.setMensajeError(mensajeError);
            job.setTsFin(LocalDateTime.now());
            jpaRepository.save(job);
        });
    }

    @Override
    public JobStatus obtener(String jobId) {
        return jpaRepository.findById(jobId)
                .map(j -> new JobStatus(j.getId(), j.getEstado(),
                        j.getProgreso(), j.getMensajeError()))
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Job no encontrado: " + jobId));
    }
}
