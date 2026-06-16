package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.Data;

@Data
public class ConfiguracionDetalleDTO {

    private String separador;
    private Integer filasASaltar;
    private Integer columnaFecha;
    private String formatoFecha;
    private Integer columnaDescripcion;
    private Integer columnaReferencia;
    private Integer columnaMonto;
    private Integer columnaDebito;
    private Integer columnaCredito;
    private Boolean debitoYCreditoSeparados;
    private String encoding;
    private Integer numeroHoja;
    private Double factorMonto;
    private String separadorMiles;
    private String separadorDecimales;

    /** Solo para tipoArchivo=TXT: "DELIMITADO" (CSV genérico) o "ANCHO_FIJO" (ej. extracto Davivienda). */
    private String formatoTxt;
}
