package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.TablaArchivo;

import java.util.ArrayList;
import java.util.List;

/**
 * Resume un archivo para la IA: las primeras filas (encabezados y primeros datos) y las
 * últimas (totales y saldos). El resto del archivo no se envía.
 */
public class MuestraTabla {

    private static final int MAX_HOJAS = 3;
    private static final int PRIMERAS = 40;
    private static final int ULTIMAS = 15;
    private static final int MAX_TEXTO = 60;
    private static final int MAX_COLUMNAS = 40;

    public String construir(TablaArchivo tabla) {
        StringBuilder muestra = new StringBuilder();
        int hojas = Math.min(tabla.hojas().size(), MAX_HOJAS);
        for (int h = 0; h < hojas; h++) {
            TablaArchivo.Hoja hoja = tabla.hojas().get(h);
            muestra.append("== Hoja ").append(h).append(" \"").append(recortar(hoja.nombre()))
                    .append("\" (").append(hoja.filas().size()).append(" filas)\n");

            List<Integer> conDatos = new ArrayList<>();
            for (int f = 0; f < hoja.filas().size(); f++) {
                if (hoja.filas().get(f).stream().anyMatch(c -> !c.vacia())) conDatos.add(f);
            }
            for (int i = 0; i < conDatos.size(); i++) {
                if (i >= PRIMERAS && i < conDatos.size() - ULTIMAS) {
                    if (i == PRIMERAS) muestra.append("... (filas intermedias omitidas)\n");
                    continue;
                }
                agregarFila(muestra, hoja, conDatos.get(i));
            }
        }
        return muestra.toString();
    }

    private void agregarFila(StringBuilder muestra, TablaArchivo.Hoja hoja, int f) {
        muestra.append("F").append(f);
        List<TablaArchivo.Celda> fila = hoja.filas().get(f);
        for (int c = 0; c < Math.min(fila.size(), MAX_COLUMNAS); c++) {
            TablaArchivo.Celda celda = fila.get(c);
            if (celda.vacia()) continue;
            muestra.append(" | c").append(c).append('=');
            if (celda.fecha() != null) {
                muestra.append("fecha:").append(celda.fecha());
            } else if (celda.numero() != null) {
                muestra.append("num:").append(celda.numero().toPlainString());
            } else {
                muestra.append('"').append(recortar(celda.texto())).append('"');
            }
        }
        muestra.append('\n');
    }

    private String recortar(String texto) {
        String limpio = texto.replaceAll("\\s+", " ");
        return limpio.length() <= MAX_TEXTO ? limpio : limpio.substring(0, MAX_TEXTO);
    }
}
