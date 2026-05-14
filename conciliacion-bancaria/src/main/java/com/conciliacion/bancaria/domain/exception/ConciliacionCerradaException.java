package com.conciliacion.bancaria.domain.exception;

public class ConciliacionCerradaException extends RuntimeException {

    public ConciliacionCerradaException(Long idConciliacion) {
        super("La conciliación " + idConciliacion
                + " está CERRADA y no puede ser modificada");
    }
}
