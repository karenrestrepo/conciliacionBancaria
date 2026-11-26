package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MovimientoPreviewDTO {

    private LocalDate fecha;
    private String descripcion;
    private BigDecimal monto;
    private String tipo;
}
