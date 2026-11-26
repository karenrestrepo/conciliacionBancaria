package com.conciliacion.bancaria.domain.model.extractoconfig;

import lombok.Data;

/** Configuración para tipoOrigen=EXCEL (XLS/XLSX, columnas por índice de celda). */
@Data
public class ConfigExcel {

    private int numeroHoja = 0;
    private int filasASaltar = 0;
    private int columnaFecha = 0;
    private String formatoFecha = "dd/MM/yyyy";
    private int columnaDescripcion = 1;
    private int columnaReferencia = -1;
    private boolean debitoYCreditoSeparados = false;
    private int columnaMonto = 4;
    private int columnaDebito = -1;
    private int columnaCredito = -1;
}
