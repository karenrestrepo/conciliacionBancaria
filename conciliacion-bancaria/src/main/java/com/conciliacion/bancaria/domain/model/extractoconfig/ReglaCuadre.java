package com.conciliacion.bancaria.domain.model.extractoconfig;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Regla opcional para validar que saldoAnterior + créditos - débitos = saldoFinal
 * antes de guardar una configuración. Los valores se ubican en el archivo buscando
 * la etiqueta literal (sin distinguir mayúsculas/minúsculas) y tomando el primer
 * número que aparece después en la misma línea/celda — no requiere regex de usuario.
 */
@Data
public class ReglaCuadre {

    private boolean habilitada = false;
    private String etiquetaSaldoAnterior;
    private String etiquetaCreditos;
    private String etiquetaDebitos;
    private String etiquetaSaldoFinal;
    private BigDecimal tolerancia = new BigDecimal("0.01");
}
