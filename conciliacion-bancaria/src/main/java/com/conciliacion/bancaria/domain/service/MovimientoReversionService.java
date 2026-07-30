package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.RequiredArgsConstructor;

/**
 * Revierte un movimiento (bancario o contable) a PENDIENTE cuando su emparejamiento
 * (sugerencia) deja de ser válido — porque se rechazó, o porque el movimiento del
 * otro lado desapareció en una recarga del auxiliar. Recrea la partida pendiente
 * que se había eliminado cuando el movimiento obtuvo su sugerencia, para que vuelva
 * a ser visible en la pantalla de cruces manuales si no encuentra pareja de nuevo.
 *
 * Compartido entre {@code CargaCsvUseCaseImpl} (auxiliar-anulado) y
 * {@code RevisionUseCaseImpl} (rechazar sugerencia) — mismo mecanismo de reversión.
 */
@RequiredArgsConstructor
public class MovimientoReversionService {

    private final MovimientoRepositoryPort movimientoRepo;
    private final PartidaRepositoryPort partidaRepo;

    public void revertirAPendiente(Long idConciliacion, Long idMovimiento, String tipoOrigen) {
        if ("BANCARIO".equals(tipoOrigen)) {
            movimientoRepo.actualizarEstadoBancario(idMovimiento, EstadoMovimiento.PENDIENTE);
        } else {
            movimientoRepo.actualizarEstadoContable(idMovimiento, EstadoMovimiento.PENDIENTE);
        }

        partidaRepo.guardar(PartidaConciliatoria.builder()
                .idConciliacion(idConciliacion)
                .idMovimiento(idMovimiento)
                .tipoOrigen(tipoOrigen)
                .estado("PENDIENTE")
                .build());
    }
}
