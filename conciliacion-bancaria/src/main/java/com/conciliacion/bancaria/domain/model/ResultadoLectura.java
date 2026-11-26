package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;

/** Qué pasó con un extracto subido para leerlo con IA. */
@Getter
@Builder
public class ResultadoLectura {

    public enum Estado {
        /** Verificado y guardado sin intervención; el motor ya está corriendo. */
        CARGADO,
        /** No se puede verificar automáticamente: el usuario decide. Nada guardado. */
        REQUIERE_CONFIRMACION,
        /** La lectura es incorrecta o el extracto ya estaba cargado. Nada guardado. */
        RECHAZADO
    }

    private final Estado estado;
    private final String mensaje;
    private final RevisionLectura revision;
    /** Solo cuando el estado es CARGADO. */
    private final String jobId;
}
