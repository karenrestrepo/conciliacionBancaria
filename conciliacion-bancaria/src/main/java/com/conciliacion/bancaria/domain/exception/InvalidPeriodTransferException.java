package com.conciliacion.bancaria.domain.exception;

public class InvalidPeriodTransferException extends RuntimeException {

    public InvalidPeriodTransferException(String periodo) {
        super("No se puede arrastrar la partida: el período destino "
                + periodo + " ya está CERRADO");
    }
}
