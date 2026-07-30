package com.conciliacion.bancaria.domain.model.extractoconfig;

import lombok.Data;

/** Configuración para tipoOrigen=DELIMITADO (CSV/TXT con separador, columnas por índice). */
@Data
public class ConfigDelimitado {

    private String separador = ",";
    private int filasASaltar = 0;
    private int columnaFecha = 0;
    private String formatoFecha = "dd/MM/yyyy";
    private int columnaDescripcion = 1;
    private int columnaReferencia = -1;
    private boolean debitoYCreditoSeparados = false;
    private int columnaMonto = 2;
    private int columnaDebito = -1;
    private int columnaCredito = -1;
    /** Si >= 0: columna con literal "DEBITO"/"CREDITO" — el monto en columnaMonto viene sin signo. */
    private int columnaTipoMovimiento = -1;
}
