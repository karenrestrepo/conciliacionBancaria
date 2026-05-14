package com.conciliacion.bancaria.domain.model;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.Builder;
import lombok.Getter;
import lombok.With;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@With

public class Movimiento {

    private final Long id;
    private final LocalDate fecha;
    private final String descripcion;
    private final BigDecimal monto;
    private final String tipo;           // "DEBITO" o "CREDITO"
    private final EstadoMovimiento estado;

    public Movimiento marcarSugerido() {
        return this.withEstado(EstadoMovimiento.SUGERIDO);
    }

    public Movimiento marcarConciliado() {
        return this.withEstado(EstadoMovimiento.CONCILIADO);
    }
}
