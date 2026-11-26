package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.ArchivoLeido;
import com.conciliacion.bancaria.domain.model.AuxiliarLeido;

/** La IA transcribe un auxiliar que no es una tabla (PDF, foto o reporte de texto). */
public interface LectorDocumentoAuxiliarIaPort {

    AuxiliarLeido leer(ArchivoLeido.Documento documento, String periodo);

    default boolean tieneRespaldo() {
        return false;
    }

    default AuxiliarLeido leerConRespaldo(ArchivoLeido.Documento documento, String periodo) {
        return leer(documento, periodo);
    }

    default boolean esSimulado() {
        return false;
    }
}
