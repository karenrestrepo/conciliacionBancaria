package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;

/** Qué pasó con un auxiliar subido para leerlo con IA. */
@Getter
@Builder
public class ResultadoAuxiliar {

    private final ResultadoLectura.Estado estado;
    private final String mensaje;
    private final RevisionAuxiliar revision;
    /** Solo para documentos pendientes de confirmación: se devuelve para no leerlos otra vez. */
    private final AuxiliarLeido lectura;
    /** Solo cuando el estado es CARGADO. */
    private final ResumenCargaAuxiliar resumen;
}
