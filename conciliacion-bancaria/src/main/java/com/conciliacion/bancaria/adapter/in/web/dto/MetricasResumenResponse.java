package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class MetricasResumenResponse {
    private long totalConciliaciones;
    private long enBorrador;
    private long enRevision;
    private long cerradas;
    private long totalMovimientosBancarios;
    private long totalMovimientosContables;
    private long totalSugerencias;
    private long sugerenciasAceptadas;
    private long sugerenciasRechazadas;
    private long sugerenciasPendientes;
    private BigDecimal diferenciaPromedio;
}