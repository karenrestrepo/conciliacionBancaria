package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.ConciliacionCerradaException;
import com.conciliacion.bancaria.domain.exception.InvalidPeriodTransferException;
import com.conciliacion.bancaria.domain.exception.PartidaPendienteException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoConciliacion;

import java.math.BigDecimal;
import java.util.List;

public class ClosureService {

    private final ConciliacionRepositoryPort conciliacionRepo;
    private final PartidaRepositoryPort partidaRepo;

    public ClosureService(ConciliacionRepositoryPort conciliacionRepo,
                          PartidaRepositoryPort partidaRepo) {
        this.conciliacionRepo = conciliacionRepo;
        this.partidaRepo = partidaRepo;
    }

    // ── Cierre de conciliación (TRD RT-05) ───────────────────────────────────

    public Conciliacion cerrar(Long idConciliacion, Long idUsuarioContador,
                               BigDecimal saldoExtracto, BigDecimal saldoAuxiliar) {

        Conciliacion conciliacion = conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conciliación no encontrada: " + idConciliacion));

        if (conciliacion.esCerrada()) {
            throw new ConciliacionCerradaException(idConciliacion);
        }

        // Validar que no haya partidas pendientes sin justificar
        List<PartidaConciliatoria> pendientes =
                partidaRepo.buscarPendientesSinJustificar(idConciliacion);

        if (!pendientes.isEmpty()) {
            throw new PartidaPendienteException(pendientes.size());
        }

        // Ejecutar transición de estado en el dominio
        Conciliacion cerrada = conciliacion.cerrar(idUsuarioContador,
                saldoExtracto, saldoAuxiliar);

        return conciliacionRepo.guardar(cerrada);
    }

    // ── Arrastre de partidas (TRD RT-05 edge case) ───────────────────────────
    // Sub-caso 1: período destino está ABIERTO → se arrastra normalmente
    // Sub-caso 2: período destino está CERRADO → se lanza excepción
    // Sub-caso 3: período destino no existe → se crea automáticamente al arrastrar

    public PartidaConciliatoria arrastrarPartida(Long idPartida,
                                                 String periodoDestino,
                                                 Long idUsuario) {

        PartidaConciliatoria partida = partidaRepo.buscarPorId(idPartida)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Partida no encontrada: " + idPartida));

        // Verificar estado del período destino
        EstadoConciliacion estadoDestino = conciliacionRepo
                .buscarPorPeriodo(periodoDestino)
                .map(Conciliacion::getEstado)
                .orElse(null); // null = período no existe aún (sub-caso 3)

        // Sub-caso 2: período destino CERRADO → error
        if (estadoDestino == EstadoConciliacion.CERRADA) {
            throw new InvalidPeriodTransferException(periodoDestino);
        }

        // Sub-caso 1 y 3: arrastrar
        PartidaConciliatoria arrastrada = partida
                .withEstado("ARRASTRADA")
                .withPeriodoArrastre(periodoDestino);

        return partidaRepo.actualizar(arrastrada);
    }

    // ── Justificación de partida ──────────────────────────────────────────────

    public PartidaConciliatoria justificar(Long idPartida,
                                           String justificacion,
                                           java.time.LocalDate fecha) {

        PartidaConciliatoria partida = partidaRepo.buscarPorId(idPartida)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Partida no encontrada: " + idPartida));

        PartidaConciliatoria justificada = partida
                .withJustificacion(justificacion)
                .withFechaJustificacion(fecha)
                .withEstado("JUSTIFICADA");

        return partidaRepo.actualizar(justificada);
    }
}