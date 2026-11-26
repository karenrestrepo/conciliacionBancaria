package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class ResultadoCuadre {

    private final boolean habilitada;
    private final BigDecimal saldoAnterior;
    private final BigDecimal creditos;
    private final BigDecimal debitos;
    private final BigDecimal saldoFinal;
    private final BigDecimal saldoCalculado;
    private final BigDecimal diferencia;
    private final boolean cuadra;
    private final List<String> advertencias;

    public static ResultadoCuadre deshabilitado() {
        return ResultadoCuadre.builder()
                .habilitada(false)
                .cuadra(true)
                .advertencias(List.of())
                .build();
    }
}
