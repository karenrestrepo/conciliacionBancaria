package com.conciliacion.bancaria.shared;

public enum EstadoMovimiento {
    PENDIENTE,
    SUGERIDO,
    CONCILIADO,
    /** Movimiento del extracto que fue incluido en un grupo de gastos bancarios. No participa en el motor de conciliación. */
    AGRUPADO
}
