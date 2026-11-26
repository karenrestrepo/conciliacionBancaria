package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.AuxiliarLeido;
import com.conciliacion.bancaria.domain.model.MapeoColumnas;
import com.conciliacion.bancaria.domain.model.ResultadoAuxiliar;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;

public interface LecturaAuxiliarIaUseCase {

    /** Lee, verifica y, si cuadra, carga el auxiliar sin intervención. */
    ResultadoAuxiliar procesar(Long idConciliacion, byte[] contenido);

    /**
     * Carga un auxiliar que no se pudo verificar, tras la confirmación del usuario. No llama a la IA:
     * recibe el mapeo (si el archivo era una tabla) o la lectura (si era un documento).
     */
    ResumenCargaAuxiliar confirmar(Long idConciliacion, byte[] contenido, MapeoColumnas mapeo,
                                   AuxiliarLeido lectura);
}
