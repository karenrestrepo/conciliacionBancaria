package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;
import java.time.LocalDate;

@Getter
@Builder
@With

public class PartidaConciliatoria {

    private final Long id;
    private final Long idConciliacion;
    private final Long idMovimiento;
    private final String tipoOrigen;          // "BANCARIO" o "CONTABLE"
    private final LocalDate fechaJustificacion;
    private final String justificacion;
    private final String estado;              // "PENDIENTE", "JUSTIFICADA", "ARRASTRADA"
    private final String periodoArrastre;     // formato YYYY-MM, si fue arrastrada

    // Detalles del movimiento asociado (cargados al listar)
    private final java.time.LocalDate fechaMovimiento;
    private final String descripcionMovimiento;
    private final java.math.BigDecimal montoMovimiento;
    private final String tipoMovimiento;      // "DEBITO" o "CREDITO"
    private final boolean esHistorica;        // partida de conciliación anterior

    public boolean estaJustificada() {
        return fechaJustificacion != null && justificacion != null && !justificacion.isBlank();
    }

}
