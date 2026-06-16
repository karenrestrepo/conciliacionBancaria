package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class MovimientoAgrupadoResponse {

    private final Long id;
    private final String descripcion;
    private final BigDecimal monto;
    private final LocalDate fecha;
    private final String tipo;
}
