package com.conciliacion.bancaria.domain.model;

import com.conciliacion.bancaria.shared.EstadoMovimiento;
import com.conciliacion.bancaria.shared.EstadoSugerencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sugerencia — modelo de dominio")
class SugerenciaTest {

    private Movimiento movimiento(Long id, String tipo) {
        return Movimiento.builder()
                .id(id).fecha(LocalDate.of(2024, 1, 15))
                .descripcion("Mov " + id).monto(new BigDecimal("100000.00"))
                .tipo(tipo).estado(EstadoMovimiento.PENDIENTE).build();
    }

    private Sugerencia sugerencia() {
        return Sugerencia.builder()
                .id(1L).idConciliacion(1L)
                .movimientoBancario(movimiento(1L, "DEBITO"))
                .movimientoContable(movimiento(10L, "DEBITO"))
                .confianza(new BigDecimal("1.00"))
                .criterio("MONTO_EXACTO")
                .estado(EstadoSugerencia.PENDIENTE_REVISION)
                .build();
    }

    @Test
    @DisplayName("aceptar debe cambiar estado a ACEPTADA")
    void aceptar() {
        assertThat(sugerencia().aceptar().getEstado())
                .isEqualTo(EstadoSugerencia.ACEPTADA);
    }

    @Test
    @DisplayName("rechazar debe cambiar estado a RECHAZADA")
    void rechazar() {
        assertThat(sugerencia().rechazar().getEstado())
                .isEqualTo(EstadoSugerencia.RECHAZADA);
    }

    @Test
    @DisplayName("reasignar debe cambiar estado a REASIGNADA")
    void reasignar() {
        assertThat(sugerencia().reasignar().getEstado())
                .isEqualTo(EstadoSugerencia.REASIGNADA);
    }

    @Test
    @DisplayName("getters retornan valores correctos")
    void getters() {
        Sugerencia s = sugerencia();
        assertThat(s.getId()).isEqualTo(1L);
        assertThat(s.getIdConciliacion()).isEqualTo(1L);
        assertThat(s.getCriterio()).isEqualTo("MONTO_EXACTO");
        assertThat(s.getConfianza()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(s.getMovimientoBancario().getId()).isEqualTo(1L);
        assertThat(s.getMovimientoContable().getId()).isEqualTo(10L);
    }
}
