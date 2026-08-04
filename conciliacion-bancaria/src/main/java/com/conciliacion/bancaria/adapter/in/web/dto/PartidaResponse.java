package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PartidaResponse {
    private Long id;
    private Long idConciliacion;
    private Long idMovimiento;
    private String tipoOrigen;
    private String estado;
    private String justificacion;
    private LocalDate fechaJustificacion;
    private String periodoArrastre;

    // Detalles del movimiento
    private LocalDate fechaMovimiento;
    private String descripcionMovimiento;
    private BigDecimal montoMovimiento;
    private String tipoMovimiento;
    private String ultimosDigitosTarjeta;

    private boolean esHistorica;
}
