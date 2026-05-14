package com.conciliacion.bancaria.domain.exception;

public class CsvValidationException extends RuntimeException {

    public CsvValidationException(String mensaje) {
        super(mensaje);
    }
}
