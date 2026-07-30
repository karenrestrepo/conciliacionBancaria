package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.Sugerencia;
import com.conciliacion.bancaria.domain.port.in.RevisionUseCase;
import com.conciliacion.bancaria.domain.port.out.EventLogPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.SugerenciaRepositoryPort;
import com.conciliacion.bancaria.domain.service.MovimientoReversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RevisionUseCaseImpl implements RevisionUseCase {

    private final SugerenciaRepositoryPort sugerenciaRepo;
    private final MovimientoRepositoryPort movimientoRepo;
    private final EventLogPort eventLog;
    private final MovimientoReversionService reversionService;

    @Override
    @Transactional(readOnly = true)
    public List<Sugerencia> obtenerSugerencias(Long idConciliacion) {
        return sugerenciaRepo.buscarPorConciliacion(idConciliacion);
    }

    @Override
    @Transactional
    public Sugerencia aceptarSugerencia(Long idSugerencia, Long idUsuario) {
        Sugerencia sugerencia = sugerenciaRepo.buscarPorId(idSugerencia)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sugerencia no encontrada: " + idSugerencia));

        Sugerencia aceptada = sugerencia.aceptar();
        Sugerencia guardada = sugerenciaRepo.actualizar(aceptada);

        // Marcar ambos movimientos como CONCILIADO
        movimientoRepo.actualizar(
                sugerencia.getMovimientoBancario().marcarConciliado(),
                sugerencia.getIdConciliacion(), "BANCARIO");
        movimientoRepo.actualizar(
                sugerencia.getMovimientoContable().marcarConciliado(),
                sugerencia.getIdConciliacion(), "CONTABLE");

        eventLog.actionAccept(sugerencia.getIdConciliacion(), idSugerencia, idUsuario);
        return guardada;
    }

    @Override
    @Transactional
    public List<Sugerencia> aceptarLote(List<Long> idsSugerencias, Long idUsuario) {
        List<Sugerencia> aceptadas = new ArrayList<>();
        for (Long id : idsSugerencias) {
            try {
                aceptadas.add(aceptarSugerencia(id, idUsuario));
            } catch (Exception e) {
                // Ignorar sugerencias que ya no están pendientes
            }
        }
        return aceptadas;
    }

    @Override
    @Transactional
    public Sugerencia rechazarSugerencia(Long idSugerencia, Long idUsuario) {
        Sugerencia sugerencia = sugerenciaRepo.buscarPorId(idSugerencia)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Sugerencia no encontrada: " + idSugerencia));

        Sugerencia rechazada = sugerencia.rechazar();
        Sugerencia guardada = sugerenciaRepo.actualizar(rechazada);

        // Se mantiene el registro RECHAZADA como histórico, pero ambos movimientos
        // deben volver a estar disponibles para el motor — antes se quedaban atascados
        // como si la sugerencia siguiera vigente.
        reversionService.revertirAPendiente(sugerencia.getIdConciliacion(),
                sugerencia.getMovimientoBancario().getId(), "BANCARIO");
        reversionService.revertirAPendiente(sugerencia.getIdConciliacion(),
                sugerencia.getMovimientoContable().getId(), "CONTABLE");

        eventLog.actionReject(sugerencia.getIdConciliacion(), idSugerencia, idUsuario);
        return guardada;
    }
}