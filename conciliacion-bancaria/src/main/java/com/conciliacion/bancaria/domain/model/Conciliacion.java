package com.conciliacion.bancaria.domain.model;

import com.conciliacion.bancaria.domain.exception.InvalidStateTransitionException;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@With
public class Conciliacion {

    private final Long id;
    private final String periodo;               // formato YYYY-MM
    private final EstadoConciliacion estado;
    private final Long idUsuarioCreador;
    private final Long idUsuarioAprobador;
    private final LocalDateTime tsCreacion;
    private final LocalDateTime tsCierre;
    private final BigDecimal saldoExtracto;
    private final BigDecimal saldoAuxiliar;
    private final BigDecimal diferenciaSaldo;
    private final List<Movimiento> movimientosBancarios;
    private final List<Movimiento> movimientosContables;
    private final List<Sugerencia> sugerencias;
    private final List<PartidaConciliatoria> partidas;

    // ── Máquina de estados (TRD RT-04) ───────────────────────────────────────
    // Única transición válida: BORRADOR → EN_REVISION → CERRADA

    public Conciliacion pasarAEnRevision() {
        if (this.estado != EstadoConciliacion.BORRADOR) {
            throw new InvalidStateTransitionException(this.estado, EstadoConciliacion.EN_REVISION);
        }
        return this.withEstado(EstadoConciliacion.EN_REVISION);
    }

    public Conciliacion cerrar(Long idAprobador, BigDecimal saldoExtracto,
                               BigDecimal saldoAuxiliar) {
        if (this.estado != EstadoConciliacion.EN_REVISION) {
            throw new InvalidStateTransitionException(this.estado, EstadoConciliacion.CERRADA);
        }
        BigDecimal diferencia = saldoExtracto.subtract(saldoAuxiliar);
        return this
                .withEstado(EstadoConciliacion.CERRADA)
                .withIdUsuarioAprobador(idAprobador)
                .withTsCierre(LocalDateTime.now())
                .withSaldoExtracto(saldoExtracto)
                .withSaldoAuxiliar(saldoAuxiliar)
                .withDiferenciaSaldo(diferencia);
    }

    public boolean esCerrada() {
        return this.estado == EstadoConciliacion.CERRADA;
    }

    public boolean tieneParidasPendientesSinJustificar() {
        if (partidas == null) return false;
        return partidas.stream()
                .filter(p -> "PENDIENTE".equals(p.getEstado()))
                .anyMatch(p -> !p.estaJustificada());
    }
}