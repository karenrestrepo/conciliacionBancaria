package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.adapter.out.ia.LectorSimuladoAdapter;
import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.service.RevisorLecturaExtracto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LecturaIaUseCase — vista previa y aprobación")
class LecturaIaUseCaseImplTest {

    private final LectorSimuladoAdapter lector = new LectorSimuladoAdapter();
    private final ConciliacionRepositoryPort conciliacionRepo = mock(ConciliacionRepositoryPort.class);
    private final CargaCsvUseCase carga = mock(CargaCsvUseCase.class);
    private final LecturaIaUseCaseImpl useCase =
            new LecturaIaUseCaseImpl(lector, new RevisorLecturaExtracto(), conciliacionRepo, carga);

    @BeforeEach
    void conciliacionExistente() {
        // Objeto real: Mockito no puede simular clases concretas en Java 25.
        Conciliacion conciliacion = Conciliacion.builder().id(1L).periodo("2026-12").build();
        when(conciliacionRepo.buscarPorId(1L)).thenReturn(Optional.of(conciliacion));
    }

    @Test
    @DisplayName("la vista previa advierte que son datos simulados y no guarda nada")
    void vistaPreviaNoGuarda() {
        var revision = useCase.previsualizar(1L, "%PDF-1.4 ejemplo".getBytes());

        assertThat(revision.getAdvertencias()).anyMatch(a -> a.startsWith("DATOS SIMULADOS"));
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("un archivo que no es PDF se rechaza")
    void noEsPdf() {
        assertThatThrownBy(() -> useCase.previsualizar(1L, "hola mundo".getBytes()))
                .isInstanceOf(CsvValidationException.class);
    }

    @Test
    @DisplayName("al aprobar una lectura que cuadra se envian los movimientos a guardar")
    void aprobar() {
        when(carga.confirmarExtractoLeido(eq(1L), anyList())).thenReturn("job-1");

        assertThat(useCase.confirmar(1L, lector.leer(new byte[0], "2026-12"), false)).isEqualTo("job-1");
    }

    @Test
    @DisplayName("una lectura que no cuadra no se guarda, ni siquiera aceptando")
    void noCuadraNoGuarda() {
        LecturaExtracto lectura = lector.leer(new byte[0], "2026-12");
        lectura.setSaldoFinal(BigDecimal.ONE);

        assertThatThrownBy(() -> useCase.confirmar(1L, lectura, true))
                .isInstanceOf(CsvValidationException.class);
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("sin saldos solo se guarda si el usuario lo confirma")
    void sinSaldosPideConfirmacion() {
        LecturaExtracto lectura = lector.leer(new byte[0], "2026-12");
        lectura.setSaldoFinal(null);

        assertThatThrownBy(() -> useCase.confirmar(1L, lectura, false))
                .isInstanceOf(CsvValidationException.class);
        useCase.confirmar(1L, lectura, true);
        verify(carga).confirmarExtractoLeido(eq(1L), anyList());
    }
}
