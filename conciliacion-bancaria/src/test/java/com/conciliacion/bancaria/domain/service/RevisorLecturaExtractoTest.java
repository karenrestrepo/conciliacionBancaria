package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.RevisionLectura;
import com.conciliacion.bancaria.domain.model.RevisionLectura.EstadoCuadre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RevisorLecturaExtracto — verificación de lo que interpreta la IA")
class RevisorLecturaExtractoTest {

    private final RevisorLecturaExtracto revisor = new RevisorLecturaExtracto();

    private static LecturaExtracto.MovimientoLeido mov(String fecha, String monto, String tipo) {
        return new LecturaExtracto.MovimientoLeido(fecha, "EJEMPLO",
                monto == null ? null : new BigDecimal(monto), tipo);
    }

    private static LecturaExtracto.LecturaExtractoBuilder cuenta(String saldoFinal) {
        return LecturaExtracto.builder()
                .tipoProducto("CUENTA")
                .saldoAnterior(new BigDecimal("1000"))
                .saldoFinal(saldoFinal == null ? null : new BigDecimal(saldoFinal))
                .movimientos(List.of(mov("2026-12-01", "500", "CREDITO"), mov("2026-12-02", "200", "DEBITO")));
    }

    @Test
    @DisplayName("caso normal: cuadra y no hay errores")
    void casoNormal() {
        RevisionLectura r = revisor.revisar(cuenta("1300").build(), "2026-12");

        assertThat(r.getEstadoCuadre()).isEqualTo(EstadoCuadre.CUADRA);
        assertThat(r.getErrores()).isEmpty();
        assertThat(r.getAdvertencias()).isEmpty();
    }

    @Test
    @DisplayName("saldo que no coincide: no cuadra")
    void noCuadra() {
        assertThat(revisor.revisar(cuenta("1500").build(), "2026-12").getEstadoCuadre())
                .isEqualTo(EstadoCuadre.NO_CUADRA);
    }

    @Test
    @DisplayName("total declarado distinto a la suma leida: no cuadra aunque el saldo coincida")
    void totalNoCoincide() {
        LecturaExtracto lectura = cuenta("1300").totalCreditos(new BigDecimal("900")).build();

        assertThat(revisor.revisar(lectura, "2026-12").getEstadoCuadre()).isEqualTo(EstadoCuadre.NO_CUADRA);
    }

    @Test
    @DisplayName("falta un saldo: no verificable")
    void faltaSaldo() {
        assertThat(revisor.revisar(cuenta(null).build(), "2026-12").getEstadoCuadre())
                .isEqualTo(EstadoCuadre.NO_VERIFICABLE);
    }

    @Test
    @DisplayName("datos ausentes o ambiguos se reportan como errores")
    void datosIncompletos() {
        LecturaExtracto lectura = cuenta("1300")
                .movimientos(List.of(mov("32/13/2026", null, "OTRO")))
                .build();

        assertThat(revisor.revisar(lectura, "2026-12").getErrores()).hasSize(3);
    }

    @Test
    @DisplayName("sin movimientos es un error")
    void sinMovimientos() {
        assertThat(revisor.revisar(cuenta("1000").movimientos(List.of()).build(), "2026-12").getErrores())
                .isNotEmpty();
    }

    @Test
    @DisplayName("fecha fuera del periodo solo genera advertencia")
    void fueraDePeriodo() {
        RevisionLectura r = revisor.revisar(cuenta("1300").build(), "2026-11");

        assertThat(r.getErrores()).isEmpty();
        assertThat(r.getAdvertencias()).anyMatch(a -> a.contains("fuera del período"));
    }

    @Test
    @DisplayName("en tarjeta de credito los debitos aumentan el saldo")
    void tarjeta() {
        LecturaExtracto lectura = cuenta("700").tipoProducto("TARJETA_CREDITO").build();

        assertThat(revisor.revisar(lectura, "2026-12").getEstadoCuadre()).isEqualTo(EstadoCuadre.CUADRA);
    }
}
