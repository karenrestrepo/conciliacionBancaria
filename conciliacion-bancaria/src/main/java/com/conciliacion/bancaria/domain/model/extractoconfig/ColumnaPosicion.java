package com.conciliacion.bancaria.domain.model.extractoconfig;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una columna de un extracto de ancho fijo, definida por posición de caracter
 * (1-based, inclusive) en vez de un grupo de regex.
 *
 * {@code campo} identifica qué representa esa porción de línea. Valores válidos:
 * "dia", "mes", "anio", "fecha", "descripcion", "monto", "debito", "credito",
 * "signo", "tipo".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnaPosicion {

    private String campo;
    private int inicio;
    private int fin;

    public String extraer(String linea) {
        if (linea == null) return "";
        int desde = Math.max(0, inicio - 1);
        int hasta = Math.min(linea.length(), fin);
        if (desde >= hasta) return "";
        return linea.substring(desde, hasta);
    }
}
