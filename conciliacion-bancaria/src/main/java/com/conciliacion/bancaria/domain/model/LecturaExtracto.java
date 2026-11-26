package com.conciliacion.bancaria.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lo que la IA interpreta de un extracto. Son datos NO verificados: antes de guardarse
 * pasan por {@code RevisorLecturaExtracto} y por la aprobación del usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LecturaExtracto {

    /** "CUENTA" o "TARJETA_CREDITO". */
    private String tipoProducto;
    private String ultimosDigitosTarjeta;
    private BigDecimal saldoAnterior;
    private BigDecimal saldoFinal;
    private BigDecimal totalCreditos;
    private BigDecimal totalDebitos;
    private List<MovimientoLeido> movimientos;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MovimientoLeido {
        /** yyyy-MM-dd. Texto para que una fecha mal leída se reporte en vez de romper la lectura. */
        private String fecha;
        private String descripcion;
        private BigDecimal monto;
        /** "DEBITO" o "CREDITO". */
        private String tipo;
    }
}
