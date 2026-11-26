package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Resultado de probar una configuración de extracto contra un archivo de
 * muestra antes de guardarla: preview de movimientos parseados + validación
 * de cuadre, para que el usuario de negocio pueda corregir la configuración
 * en el wizard sin tener que guardarla primero.
 */
@Getter
@Builder
public class ResultadoPruebaConfiguracion {

    private final List<Movimiento> movimientos;
    private final int totalMovimientos;
    private final ResultadoCuadre cuadre;
    private final List<String> advertencias;
}
