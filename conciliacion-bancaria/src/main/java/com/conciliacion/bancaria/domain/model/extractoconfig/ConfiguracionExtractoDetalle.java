package com.conciliacion.bancaria.domain.model.extractoconfig;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Schema tipado único de configuración de extracto bancario — reemplaza el JSON
 * libre que antes vivía en {@code ConfiguracionExtracto.configuracionDetalle} y el
 * {@code ConfiguracionDetalleDTO} incompleto. Es el mismo objeto tanto para la
 * validación/edición web como para lo que el motor de parseo consume: no hay dos
 * caminos distintos para escribir y para leer.
 */
@Data
public class ConfiguracionExtractoDetalle {

    private TipoOrigenExtracto tipoOrigen;
    private String encoding = "UTF-8";
    private BigDecimal factorMonto = BigDecimal.ONE;
    private String separadorMiles = ".";
    private String separadorDecimales = ",";

    /** No-null si tipoOrigen=DELIMITADO. */
    private ConfigDelimitado delimitado;
    /** No-null si tipoOrigen=ANCHO_FIJO. */
    private ConfigAnchoFijo anchoFijo;
    /** No-null si tipoOrigen=EXCEL. */
    private ConfigExcel excel;

    private ReglaContinuacion continuacion = new ReglaContinuacion();
    private ReglaCuadre cuadre = new ReglaCuadre();
}
