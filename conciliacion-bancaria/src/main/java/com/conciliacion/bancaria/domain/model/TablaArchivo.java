package com.conciliacion.bancaria.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contenido de un archivo tabular (Excel o texto separado) tal como viene, sin interpretar. */
public record TablaArchivo(List<Hoja> hojas) {

    public record Hoja(String nombre, List<List<Celda>> filas) {
        /** Celda en la posición pedida; vacía si la fila o la columna no existen. Índices desde 0. */
        public Celda celda(int fila, int columna) {
            if (fila < 0 || fila >= filas.size() || columna < 0 || columna >= filas.get(fila).size()) {
                return Celda.VACIA;
            }
            return filas.get(fila).get(columna);
        }
    }

    /**
     * @param texto  contenido como texto, nunca nulo
     * @param numero solo si el archivo guarda la celda como número
     * @param fecha  solo si el archivo guarda la celda como fecha
     */
    public record Celda(String texto, BigDecimal numero, LocalDate fecha) {
        public static final Celda VACIA = new Celda("", null, null);

        public static Celda deTexto(String texto) {
            return texto == null || texto.isBlank() ? VACIA : new Celda(texto.trim(), null, null);
        }

        public boolean vacia() {
            return texto.isEmpty() && numero == null && fecha == null;
        }
    }
}
