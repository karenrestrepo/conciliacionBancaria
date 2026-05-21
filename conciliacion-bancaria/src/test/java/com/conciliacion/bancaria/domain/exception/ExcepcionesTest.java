package com.conciliacion.bancaria.domain.exception;

import com.conciliacion.bancaria.shared.EstadoConciliacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Excepciones de dominio — mensajes correctos")
class ExcepcionesTest {

    @Test
    @DisplayName("ConciliacionCerradaException contiene el id en el mensaje")
    void conciliacionCerrada() {
        ConciliacionCerradaException ex = new ConciliacionCerradaException(42L);
        assertThat(ex.getMessage()).contains("42").contains("CERRADA");
    }

    @Test
    @DisplayName("CsvValidationException conserva el mensaje")
    void csvValidation() {
        CsvValidationException ex = new CsvValidationException("columna faltante");
        assertThat(ex.getMessage()).isEqualTo("columna faltante");
    }

    @Test
    @DisplayName("InvalidPeriodTransferException contiene el periodo")
    void invalidPeriod() {
        InvalidPeriodTransferException ex = new InvalidPeriodTransferException("2024-01");
        assertThat(ex.getMessage()).contains("2024-01").contains("CERRADO");
    }

    @Test
    @DisplayName("InvalidStateTransitionException contiene estados origen y destino")
    void invalidState() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                EstadoConciliacion.BORRADOR, EstadoConciliacion.CERRADA);
        assertThat(ex.getMessage()).contains("BORRADOR").contains("CERRADA");
    }

    @Test
    @DisplayName("PartidaPendienteException contiene la cantidad")
    void partidaPendiente() {
        PartidaPendienteException ex = new PartidaPendienteException(3L);
        assertThat(ex.getMessage()).contains("3");
    }
}