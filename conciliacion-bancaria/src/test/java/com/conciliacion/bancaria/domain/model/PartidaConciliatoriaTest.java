package com.conciliacion.bancaria.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PartidaConciliatoria — modelo de dominio")
class PartidaConciliatoriaTest {

    private PartidaConciliatoria partida() {
        return PartidaConciliatoria.builder()
                .id(1L).idConciliacion(1L).idMovimiento(5L)
                .tipoOrigen("BANCARIO").estado("PENDIENTE").build();
    }

    @Test
    @DisplayName("estaJustificada retorna false si no tiene justificacion")
    void noJustificada() {
        assertThat(partida().estaJustificada()).isFalse();
    }

    @Test
    @DisplayName("estaJustificada retorna true cuando tiene fecha y texto")
    void justificada() {
        PartidaConciliatoria p = partida()
                .withJustificacion("Diferencia por comision")
                .withFechaJustificacion(LocalDate.of(2024, 1, 31));
        assertThat(p.estaJustificada()).isTrue();
    }

    @Test
    @DisplayName("estaJustificada retorna false si justificacion es blank")
    void justificacionBlank() {
        PartidaConciliatoria p = partida()
                .withJustificacion("   ")
                .withFechaJustificacion(LocalDate.of(2024, 1, 31));
        assertThat(p.estaJustificada()).isFalse();
    }

    @Test
    @DisplayName("estaJustificada retorna false si fecha es null aunque tenga texto")
    void sinFecha() {
        PartidaConciliatoria p = partida().withJustificacion("texto");
        assertThat(p.estaJustificada()).isFalse();
    }

    @Test
    @DisplayName("getters retornan valores correctos")
    void getters() {
        PartidaConciliatoria p = partida();
        assertThat(p.getId()).isEqualTo(1L);
        assertThat(p.getIdConciliacion()).isEqualTo(1L);
        assertThat(p.getIdMovimiento()).isEqualTo(5L);
        assertThat(p.getTipoOrigen()).isEqualTo("BANCARIO");
        assertThat(p.getEstado()).isEqualTo("PENDIENTE");
    }
}
