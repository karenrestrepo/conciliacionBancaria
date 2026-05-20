package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.InvalidStateTransitionException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Conciliacion — máquina de estados")
class ConciliacionTest {

    private Conciliacion conciliacionEnEstado(EstadoConciliacion estado) {
        return Conciliacion.builder()
                .id(1L)
                .periodo("2024-01")
                .estado(estado)
                .idUsuarioCreador(1L)
                .build();
    }

    @Nested
    @DisplayName("Transición BORRADOR → EN_REVISION")
    class BorradorARevision {

        @Test
        @DisplayName("debe transicionar correctamente desde BORRADOR")
        void debeTransicionarDesdeBorador() {
            var conciliacion = conciliacionEnEstado(EstadoConciliacion.BORRADOR);

            var resultado = conciliacion.pasarAEnRevision();

            assertThat(resultado.getEstado()).isEqualTo(EstadoConciliacion.EN_REVISION);
        }

        @Test
        @DisplayName("debe lanzar excepción si ya está EN_REVISION")
        void debeLanzarExcepcionDesdeEnRevision() {
            var conciliacion = conciliacionEnEstado(EstadoConciliacion.EN_REVISION);

            assertThatThrownBy(conciliacion::pasarAEnRevision)
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("EN_REVISION");
        }

        @Test
        @DisplayName("debe lanzar excepción si ya está CERRADA")
        void debeLanzarExcepcionDesdeCerrada() {
            var conciliacion = conciliacionEnEstado(EstadoConciliacion.CERRADA);

            assertThatThrownBy(conciliacion::pasarAEnRevision)
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("CERRADA");
        }
    }

    @Nested
    @DisplayName("Transición EN_REVISION → CERRADA")
    class RevisionACerrada {

        @Test
        @DisplayName("debe cerrar correctamente desde EN_REVISION")
        void debeCerrarDesdeEnRevision() {
            var conciliacion = conciliacionEnEstado(EstadoConciliacion.EN_REVISION);
            var saldoExtracto = new BigDecimal("1000000.00");
            var saldoAuxiliar = new BigDecimal("950000.00");

            var resultado = conciliacion.cerrar(2L, saldoExtracto, saldoAuxiliar);

            assertThat(resultado.getEstado()).isEqualTo(EstadoConciliacion.CERRADA);
            assertThat(resultado.getIdUsuarioAprobador()).isEqualTo(2L);
            assertThat(resultado.getTsCierre()).isNotNull();
            assertThat(resultado.getDiferenciaSaldo())
                    .isEqualByComparingTo(new BigDecimal("50000.00"));
        }

        @Test
        @DisplayName("debe lanzar excepción si intenta cerrar desde BORRADOR")
        void debeLanzarExcepcionDesdeBorrador() {
            var conciliacion = conciliacionEnEstado(EstadoConciliacion.BORRADOR);

            assertThatThrownBy(() ->
                    conciliacion.cerrar(2L, BigDecimal.TEN, BigDecimal.ONE))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("BORRADOR");
        }

        @Test
        @DisplayName("debe lanzar excepción si intenta cerrar una ya CERRADA")
        void debeLanzarExcepcionDesdeCerrada() {
            var conciliacion = conciliacionEnEstado(EstadoConciliacion.CERRADA);

            assertThatThrownBy(() ->
                    conciliacion.cerrar(2L, BigDecimal.TEN, BigDecimal.ONE))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("CERRADA");
        }
    }

    @Nested
    @DisplayName("Estado y propiedades")
    class EstadoPropiedades {

        @Test
        @DisplayName("esCerrada debe retornar true solo cuando está CERRADA")
        void esCerradaSoloCuandoCerrada() {
            assertThat(conciliacionEnEstado(EstadoConciliacion.BORRADOR).esCerrada()).isFalse();
            assertThat(conciliacionEnEstado(EstadoConciliacion.EN_REVISION).esCerrada()).isFalse();
            assertThat(conciliacionEnEstado(EstadoConciliacion.CERRADA).esCerrada()).isTrue();
        }

        @Test
        @DisplayName("la diferencia de saldo se calcula correctamente al cerrar")
        void diferenciaCalculadaCorrectamente() {
            var conciliacion = conciliacionEnEstado(EstadoConciliacion.EN_REVISION);

            var resultado = conciliacion.cerrar(1L,
                    new BigDecimal("500000.00"),
                    new BigDecimal("500000.00"));

            assertThat(resultado.getDiferenciaSaldo())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}