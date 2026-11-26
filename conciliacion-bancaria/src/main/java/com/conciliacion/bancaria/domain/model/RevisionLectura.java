package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** Resultado de verificar con código una {@link LecturaExtracto}. */
@Getter
@Builder(toBuilder = true)
public class RevisionLectura {

    public enum EstadoCuadre { CUADRA, NO_CUADRA, NO_VERIFICABLE }

    private final LecturaExtracto lectura;
    private final BigDecimal sumaCreditos;
    private final BigDecimal sumaDebitos;
    private final BigDecimal saldoCalculado;
    private final BigDecimal diferencia;
    private final EstadoCuadre estadoCuadre;
    /** Impiden aprobar. */
    private final List<String> errores;
    /** Informan al usuario; no impiden aprobar. */
    private final List<String> advertencias;
}
