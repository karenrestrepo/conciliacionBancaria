package com.conciliacion.bancaria.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Lo que la IA transcribe de un auxiliar en documento. Son datos NO verificados. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuxiliarLeido {

    private BigDecimal saldoInicial;
    private BigDecimal saldoFinal;
    private BigDecimal totalDebitos;
    private BigDecimal totalCreditos;
    private List<Fila> movimientos;

    /** Débito y crédito son los contables, tal como aparecen en el auxiliar. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fila {
        /** yyyy-MM-dd. */
        private String fecha;
        private String comprobante;
        private String descripcion;
        private BigDecimal debito;
        private BigDecimal credito;
    }
}
