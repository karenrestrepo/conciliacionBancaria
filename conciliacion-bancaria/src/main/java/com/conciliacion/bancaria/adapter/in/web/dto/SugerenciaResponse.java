package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
public class SugerenciaResponse {
    private Long id;
    private Long idConciliacion;
    private BigDecimal confianza;
    private String criterio;
    private String estado;
    // Movimiento bancario
    private Long idMovBancario;
    private LocalDate fechaBancario;
    private String descripcionBancario;
    private BigDecimal montoBancario;
    private String tipoBancario;
    private String ultimosDigitosTarjeta;
    // Movimiento contable
    private Long idMovContable;
    private LocalDate fechaContable;
    private String descripcionContable;
    private BigDecimal montoContable;
    private String tipoContable;
}