package com.conciliacion.bancaria.domain.exception;

import com.conciliacion.bancaria.shared.EstadoConciliacion;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(EstadoConciliacion estadoActual,
                                           EstadoConciliacion estadoSolicitado) {
        super("Transición inválida: no se puede pasar de "
                + estadoActual + " a " + estadoSolicitado);
    }
}