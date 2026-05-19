package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.port.in.JobStatusUseCase;
import com.conciliacion.bancaria.domain.port.out.JobRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobStatusUseCaseImpl implements JobStatusUseCase {

    private final JobRepositoryPort jobRepo;

    @Override
    public JobStatus obtenerEstado(String jobId) {
        return jobRepo.obtener(jobId);
    }
}