package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.MapeoExtracto;

/** La IA recibe una muestra de un extracto en Excel o texto y dice dónde está cada dato. */
public interface MapeadorExtractoIaPort {

    MapeoExtracto mapear(String muestra, String periodo);

    default boolean tieneRespaldo() {
        return false;
    }

    default MapeoExtracto mapearConRespaldo(String muestra, String periodo) {
        return mapear(muestra, periodo);
    }

    default boolean esSimulado() {
        return false;
    }
}
