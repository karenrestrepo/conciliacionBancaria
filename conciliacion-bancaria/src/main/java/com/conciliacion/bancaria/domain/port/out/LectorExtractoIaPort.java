package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.LecturaExtracto;

/** Puerto intercambiable: permite cambiar de proveedor de IA sin tocar el resto de la aplicación. */
public interface LectorExtractoIaPort {

    LecturaExtracto leer(byte[] pdf, String periodo);

    /** true si se puede repetir la lectura con un modelo más capaz cuando la primera no cuadra. */
    default boolean tieneRespaldo() {
        return false;
    }

    default LecturaExtracto leerConRespaldo(byte[] pdf, String periodo) {
        return leer(pdf, periodo);
    }

    /** true si devuelve datos inventados para probar el flujo sin llamar a ningún proveedor. */
    default boolean esSimulado() {
        return false;
    }
}
