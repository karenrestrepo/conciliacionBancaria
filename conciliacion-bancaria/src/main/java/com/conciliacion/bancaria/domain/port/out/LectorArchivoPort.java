package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.ArchivoAuxiliar;

public interface LectorArchivoPort {

    /** Reconoce el tipo de archivo por su contenido. Lanza CsvValidationException si no puede abrirlo. */
    ArchivoAuxiliar leer(byte[] contenido);
}
