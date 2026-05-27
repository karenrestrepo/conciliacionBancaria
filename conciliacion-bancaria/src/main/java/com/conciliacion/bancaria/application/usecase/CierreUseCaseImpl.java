package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.port.in.CierreUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.EventLogPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.domain.service.ClosureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CierreUseCaseImpl implements CierreUseCase {

    private final ClosureService closureService;
    private final ConciliacionRepositoryPort conciliacionRepo;
    private final MovimientoRepositoryPort movimientoRepo;
    private final PartidaRepositoryPort partidaRepo;
    private final EventLogPort eventLog;

    @Override
    @Transactional
    public Conciliacion cerrar(Long idConciliacion, Long idUsuarioContador) {
        // Calcular saldos desde los movimientos persistidos
        List<com.conciliacion.bancaria.domain.model.Movimiento> bancarios =
                movimientoRepo.buscarBancariosPorConciliacion(idConciliacion);
        List<com.conciliacion.bancaria.domain.model.Movimiento> contables =
                movimientoRepo.buscarContablesPorConciliacion(idConciliacion);

        BigDecimal saldoExtracto = bancarios.stream()
                .map(m -> "CREDITO".equals(m.getTipo())
                        ? m.getMonto() : m.getMonto().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoAuxiliar = contables.stream()
                .map(m -> "CREDITO".equals(m.getTipo())
                        ? m.getMonto() : m.getMonto().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Conciliacion cerrada = closureService.cerrar(
                idConciliacion, idUsuarioContador, saldoExtracto, saldoAuxiliar);

        eventLog.closeConciliacion(idConciliacion, idUsuarioContador);
        return cerrada;
    }

    @Override
    @Transactional
    public PartidaConciliatoria justificarPartida(Long idPartida,
                                                  String justificacion,
                                                  LocalDate fecha) {
        return closureService.justificar(idPartida, justificacion, fecha);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartidaConciliatoria> listarPartidas(Long idConciliacion) {
        return partidaRepo.buscarPorConciliacion(idConciliacion);
    }
}