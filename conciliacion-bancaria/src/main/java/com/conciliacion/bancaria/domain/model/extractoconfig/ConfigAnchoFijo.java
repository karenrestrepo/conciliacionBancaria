package com.conciliacion.bancaria.domain.model.extractoconfig;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Configuración para tipoOrigen=ANCHO_FIJO (reporte de impresión, columnas por posición de caracter). */
@Data
public class ConfigAnchoFijo {

    private List<ColumnaPosicion> columnas = new ArrayList<>();
    private ConvencionSigno convencionSigno = ConvencionSigno.SUFIJO;
    /** Usado solo si hay una columna "fecha" completa (en vez de "dia"+"mes" separados). */
    private String formatoFecha = "dd/MM/yyyy";
    /**
     * Invierte el resultado DEBITO/CREDITO de SUFIJO/PREFIJO/COLUMNA_TIPO. Necesario en
     * tarjetas de crédito, donde el signo del extracto refleja cargo/pago desde la
     * perspectiva del tarjetahabiente ("+"=cargo, "-"=pago) en vez de crédito/débito
     * contable. No aplica a COLUMNAS_SEPARADAS (ahí la columna ya es inequívoca).
     */
    private boolean invertir = false;

    public ColumnaPosicion columna(String campo) {
        if (columnas == null) return null;
        return columnas.stream()
                .filter(c -> campo.equals(c.getCampo()))
                .findFirst()
                .orElse(null);
    }
}
