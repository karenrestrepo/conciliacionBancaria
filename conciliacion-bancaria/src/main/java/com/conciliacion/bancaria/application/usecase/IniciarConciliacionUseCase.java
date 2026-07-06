package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.port.in.ConciliacionUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.EventLogPort;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IniciarConciliacionUseCase implements ConciliacionUseCase {

    private final ConciliacionRepositoryPort conciliacionRepo;
    private final EventLogPort eventLog;

    @Override
    @Transactional
    public Conciliacion iniciar(String periodo, Long idUsuario, Long idCuenta, Long empresaId) {
        if (conciliacionRepo.existePorPeriodoYCuenta(periodo, idCuenta)) {
            throw new IllegalStateException(
                    "Ya existe una conciliación para el período " + periodo
                            + " con la cuenta indicada");
        }

        Conciliacion nueva = Conciliacion.builder()
                .periodo(periodo)
                .idCuenta(idCuenta)
                .empresaId(empresaId)
                .estado(EstadoConciliacion.BORRADOR)
                .idUsuarioCreador(idUsuario)
                .build();

        Conciliacion guardada = conciliacionRepo.guardar(nueva);
        eventLog.initConciliacion(guardada.getId(), idUsuario);
        return guardada;
    }

    @Override
    @Transactional(readOnly = true)
    public Conciliacion obtenerPorId(Long id) {
        return conciliacionRepo.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conciliación no encontrada: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conciliacion> listarPorUsuario(Long idUsuario) {
        return conciliacionRepo.buscarPorUsuarioCreador(idUsuario);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conciliacion> listarPorEmpresa(Long empresaId) {
        return conciliacionRepo.buscarPorEmpresa(empresaId);
    }

    @Override
    @Transactional
    public Conciliacion pasarARevision(Long idConciliacion) {
        Conciliacion conciliacion = conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conciliación no encontrada: " + idConciliacion));

        Conciliacion enRevision = conciliacion.pasarAEnRevision();
        Conciliacion guardada = conciliacionRepo.guardar(enRevision);
        eventLog.openReview(guardada.getId());
        return guardada;
    }
}