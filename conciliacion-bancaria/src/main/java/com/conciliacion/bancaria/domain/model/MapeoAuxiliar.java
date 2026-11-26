package com.conciliacion.bancaria.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cómo está organizado un auxiliar contable, según la IA. No contiene movimientos:
 * el código lee las filas con estas indicaciones y verifica el resultado.
 * Filas y columnas empiezan en 0; una columna ausente es -1 o nulo.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MapeoAuxiliar {

    private int hoja;
    private int primeraFilaDatos;
    private Integer columnaFecha;
    private Integer columnaComprobante;
    private List<Integer> columnasDescripcion;
    /** "DEBITO_CREDITO", "MONTO_CON_SIGNO" o "MONTO_Y_NATURALEZA". */
    private String esquemaMonto;
    private Integer columnaDebito;
    private Integer columnaCredito;
    private Integer columnaMonto;
    private Integer columnaNaturaleza;
    /** Valores de la columna de naturaleza que significan débito contable (ej. "D", "DB"). */
    private List<String> valoresDebito;
    private boolean positivoEsDebito;
    /** "." o ",". Solo aplica a valores guardados como texto. */
    private String separadorDecimal;
    /** "DMA", "MDA" o "AMD". Solo aplica a fechas guardadas como texto. */
    private String ordenFecha;
    /** Celdas con los saldos y totales del propio archivo, para verificar la lectura. */
    private Posicion saldoInicial;
    private Posicion saldoFinal;
    private Posicion totalDebitos;
    private Posicion totalCreditos;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Posicion {
        private int fila;
        private int columna;
    }
}
