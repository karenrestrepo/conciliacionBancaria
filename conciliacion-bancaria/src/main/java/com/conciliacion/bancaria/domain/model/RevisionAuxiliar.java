package com.conciliacion.bancaria.domain.model;

import com.conciliacion.bancaria.domain.model.RevisionLectura.EstadoCuadre;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** Resultado de leer y verificar con código un auxiliar. Débitos y créditos son los contables. */
@Getter
@Builder(toBuilder = true)
public class RevisionAuxiliar {

    private final MapeoAuxiliar mapeo;
    /** Tal como se guardarían: el débito contable entra al banco (CREDITO). */
    private final List<LecturaExtracto.MovimientoLeido> movimientos;
    private final BigDecimal sumaDebitos;
    private final BigDecimal sumaCreditos;
    private final BigDecimal totalDebitosArchivo;
    private final BigDecimal totalCreditosArchivo;
    private final BigDecimal saldoInicial;
    private final BigDecimal saldoFinal;
    private final EstadoCuadre estadoCuadre;
    /** Impiden cargar. */
    private final List<String> errores;
    /** Informan al usuario; no impiden cargar. */
    private final List<String> advertencias;
}
