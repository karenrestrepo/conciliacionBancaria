package com.conciliacion.bancaria.domain.service.parser.support;

import java.math.BigDecimal;

/**
 * Parseo de montos con separador de miles/decimales configurables y factor de escala.
 * Unifica la lógica que antes vivía duplicada en cada parser de extracto.
 */
public class MontoParser {

    public BigDecimal parsear(String texto, String separadorMiles, String separadorDecimales,
                               BigDecimal factor) {
        if (texto == null) return BigDecimal.ZERO;
        try {
            String t = texto.trim().replaceAll("[^\\d.,\\-+]", "");
            if (separadorMiles != null && !separadorMiles.isEmpty()) {
                t = t.replace(separadorMiles, "");
            }
            if (separadorDecimales != null && !separadorDecimales.isEmpty()
                    && !separadorDecimales.equals(".")) {
                t = t.replace(separadorDecimales, ".");
            }
            if (t.isEmpty() || t.equals("-") || t.equals("+")) return BigDecimal.ZERO;
            BigDecimal valor = new BigDecimal(t);
            return factor != null ? valor.multiply(factor) : valor;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
