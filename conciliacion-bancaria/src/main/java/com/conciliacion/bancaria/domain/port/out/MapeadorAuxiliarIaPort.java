package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.MapeoAuxiliar;

/** La IA recibe una muestra del archivo y dice dónde está cada dato. */
public interface MapeadorAuxiliarIaPort {

    MapeoAuxiliar mapear(String muestra);

    default boolean tieneRespaldo() {
        return false;
    }

    default MapeoAuxiliar mapearConRespaldo(String muestra) {
        return mapear(muestra);
    }

    default boolean esSimulado() {
        return false;
    }
}
