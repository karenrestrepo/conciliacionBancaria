package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.MapeoColumnas;

/** La IA recibe una muestra del archivo y dice dónde está cada dato. */
public interface MapeadorAuxiliarIaPort {

    MapeoColumnas mapear(String muestra);

    default boolean tieneRespaldo() {
        return false;
    }

    default MapeoColumnas mapearConRespaldo(String muestra) {
        return mapear(muestra);
    }

    default boolean esSimulado() {
        return false;
    }
}
