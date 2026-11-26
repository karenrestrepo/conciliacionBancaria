package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Resultado de recargar el libro auxiliar contable: cuántos renglones nuevos entraron,
 * cuántos contables se dieron de baja porque desaparecieron del archivo re-subido, y
 * cuántos de esos requirieron revertir un emparejamiento (sugerencia activa). Reemplaza
 * el "jobId" plano que antes se retornaba sin contexto de qué pasó en la carga.
 */
@Getter
@Builder
public class ResumenCargaAuxiliar {

    /** Null si no había renglones nuevos que enviar al motor incremental. */
    private final String jobId;
    private final int nuevos;
    private final int anulados;
    private final int revertidos;
}
