package com.conciliacion.bancaria.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cómo está organizado un extracto en Excel o texto, según la IA. Abono es el dinero que entra
 * (o un pago a la tarjeta); cargo es el que sale (o una compra). Filas y columnas empiezan en 0.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MapeoExtracto {

    /** "CUENTA" o "TARJETA_CREDITO". */
    private String tipoProducto;
    private String ultimosDigitosTarjeta;
    private int hoja;
    private int primeraFilaDatos;
    private Integer columnaFecha;
    private List<Integer> columnasDescripcion;
    /** "ABONO_CARGO", "MONTO_CON_SIGNO" o "MONTO_Y_NATURALEZA". */
    private String esquemaMonto;
    private Integer columnaAbonos;
    private Integer columnaCargos;
    private Integer columnaMonto;
    private Integer columnaNaturaleza;
    private List<String> valoresAbono;
    private boolean positivoEsAbono;
    private String separadorDecimal;
    private String ordenFecha;
    private MapeoColumnas.Posicion saldoAnterior;
    private MapeoColumnas.Posicion saldoFinal;
    private MapeoColumnas.Posicion totalAbonos;
    private MapeoColumnas.Posicion totalCargos;
}
