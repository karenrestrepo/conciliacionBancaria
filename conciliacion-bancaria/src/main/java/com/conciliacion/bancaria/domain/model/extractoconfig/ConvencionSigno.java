package com.conciliacion.bancaria.domain.model.extractoconfig;

/** Cómo se determina si un movimiento de ancho fijo es DEBITO o CREDITO. */
public enum ConvencionSigno {
    /** El monto trae el signo +/- al final (ej. "$2,364,110.00-"). */
    SUFIJO,
    /** El monto trae el signo +/- al inicio (ej. "-$2,364,110.00"). */
    PREFIJO,
    /** Débito y crédito vienen en columnas de posición distintas. */
    COLUMNAS_SEPARADAS,
    /** Una columna literal indica el tipo (ej. "CREDITO"/"DEBITO", "C"/"D"). */
    COLUMNA_TIPO
}
