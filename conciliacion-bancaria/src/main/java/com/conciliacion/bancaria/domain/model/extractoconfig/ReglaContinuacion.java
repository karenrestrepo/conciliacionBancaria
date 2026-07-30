package com.conciliacion.bancaria.domain.model.extractoconfig;

import lombok.Data;

/**
 * Regla para fusionar una línea/fila de continuación (sin fecha propia) con el
 * movimiento anterior — cubre descripciones que continúan en una segunda línea física.
 */
@Data
public class ReglaContinuacion {

    private boolean habilitada = false;
    /** Campo cuya ausencia marca una línea como posible continuación (normalmente "fecha"). */
    private String campoAncla = "fecha";
    /** Campo del movimiento anterior al que se concatena el contenido de la línea (normalmente "descripcion"). */
    private String campoDestino = "descripcion";
}
