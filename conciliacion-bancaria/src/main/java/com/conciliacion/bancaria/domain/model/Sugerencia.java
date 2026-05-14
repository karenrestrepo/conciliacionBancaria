package com.conciliacion.bancaria.domain.model;

import com.conciliacion.bancaria.shared.EstadoSugerencia;
import lombok.Builder;
import lombok.Getter;
import lombok.With;
import java.math.BigDecimal;

@Getter
@Builder
@With

public class Sugerencia {

    private final Long id;
    private final Long idConciliacion;
    private final Movimiento movimientoBancario;
    private final Movimiento movimientoContable;
    private final BigDecimal confianza;   // 0.0 a 1.0
    private final String criterio;        // "MONTO_EXACTO", "MONTO_FECHA_PROXIMA"
    private final EstadoSugerencia estado;

    public Sugerencia aceptar() {
        return this.withEstado(EstadoSugerencia.ACEPTADA);
    }

    public Sugerencia rechazar() {
        return this.withEstado(EstadoSugerencia.RECHAZADA);
    }

    public Sugerencia reasignar() {
        return this.withEstado(EstadoSugerencia.REASIGNADA);
    }

}
