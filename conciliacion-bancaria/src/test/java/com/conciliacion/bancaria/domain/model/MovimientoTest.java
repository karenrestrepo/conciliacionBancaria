package com.conciliacion.bancaria.domain.model;

import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Movimiento — modelo de dominio")
class MovimientoTest {

    private Movimiento movimiento() {
        return Movimiento.builder()
                .id(1L)
                .fecha(LocalDate.of(2024, 1, 15))
                .descripcion("Pago proveedor")
                .monto(new BigDecimal("150000.00"))
                .tipo("DEBITO")
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }

    @Test
    @DisplayName("marcarSugerido debe cambiar estado a SUGERIDO")
    void marcarSugerido() {
        Movimiento resultado = movimiento().marcarSugerido();
        assertThat(resultado.getEstado()).isEqualTo(EstadoMovimiento.SUGERIDO);
    }

    @Test
    @DisplayName("marcarConciliado debe cambiar estado a CONCILIADO")
    void marcarConciliado() {
        Movimiento resultado = movimiento().marcarConciliado();
        assertThat(resultado.getEstado()).isEqualTo(EstadoMovimiento.CONCILIADO);
    }

    @Test
    @DisplayName("marcarSugerido no muta el original — inmutabilidad")
    void inmutabilidadSugerido() {
        Movimiento original = movimiento();
        original.marcarSugerido();
        assertThat(original.getEstado()).isEqualTo(EstadoMovimiento.PENDIENTE);
    }

    @Test
    @DisplayName("getters retornan los valores correctos")
    void gettersCorrectos() {
        Movimiento m = movimiento();
        assertThat(m.getId()).isEqualTo(1L);
        assertThat(m.getFecha()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(m.getDescripcion()).isEqualTo("Pago proveedor");
        assertThat(m.getMonto()).isEqualByComparingTo(new BigDecimal("150000.00"));
        assertThat(m.getTipo()).isEqualTo("DEBITO");
    }
}