package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.ArchivoLeido;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;

/** La IA transcribe un extracto en PDF, foto o texto sin columnas. Permite cambiar de proveedor. */
public interface LectorExtractoIaPort {

    LecturaExtracto leer(ArchivoLeido.Documento documento, String periodo);

    /** true si se puede repetir la lectura con un modelo más capaz cuando la primera no cuadra. */
    default boolean tieneRespaldo() {
        return false;
    }

    default LecturaExtracto leerConRespaldo(ArchivoLeido.Documento documento, String periodo) {
        return leer(documento, periodo);
    }

    /** true si devuelve datos inventados para probar el flujo sin llamar a ningún proveedor. */
    default boolean esSimulado() {
        return false;
    }
}
