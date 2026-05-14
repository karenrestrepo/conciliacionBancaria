package com.conciliacion.bancaria.domain.exception;

public class PartidaPendienteException extends RuntimeException {

    public PartidaPendienteException(long cantidad) {
        super("No se puede cerrar la conciliación: hay "
                + cantidad + " partida(s) pendiente(s) sin justificación");
    }
}