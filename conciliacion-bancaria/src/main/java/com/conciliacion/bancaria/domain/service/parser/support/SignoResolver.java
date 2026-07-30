package com.conciliacion.bancaria.domain.service.parser.support;

import com.conciliacion.bancaria.domain.model.extractoconfig.ConvencionSigno;

/**
 * Unifica las 4 convenciones de signo de un extracto de ancho fijo en un solo
 * punto (antes repartidas entre el parser TXT y el XLSX).
 */
public class SignoResolver {

    /**
     * Para SUFIJO/PREFIJO: determina si el texto crudo del campo monto (que
     * incluye el signo) representa un crédito.
     */
    public boolean esCreditoPorSigno(String textoConSigno, ConvencionSigno convencion) {
        if (textoConSigno == null) return false;
        String t = textoConSigno.trim();
        if (t.isEmpty()) return false;
        char c = convencion == ConvencionSigno.PREFIJO ? t.charAt(0) : t.charAt(t.length() - 1);
        return c == '+';
    }

    /** Quita el caracter de signo del texto para que MontoParser reciba solo dígitos/separadores. */
    public String limpiarSigno(String textoConSigno, ConvencionSigno convencion) {
        if (textoConSigno == null) return null;
        String t = textoConSigno.trim();
        if (t.isEmpty()) return t;
        if (convencion == ConvencionSigno.PREFIJO && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            return t.substring(1);
        }
        if (convencion == ConvencionSigno.SUFIJO
                && (t.charAt(t.length() - 1) == '+' || t.charAt(t.length() - 1) == '-')) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }

    /** Para COLUMNA_TIPO: interpreta un literal tipo "CREDITO"/"C" vs "DEBITO"/"D". */
    public boolean esCreditoPorTipoLiteral(String tipoTexto) {
        if (tipoTexto == null) return false;
        String t = tipoTexto.trim().toUpperCase();
        return t.startsWith("C");
    }

    public String tipoDe(boolean esCredito) {
        return esCredito ? "CREDITO" : "DEBITO";
    }
}
