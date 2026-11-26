package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.Sugerencia;
import com.conciliacion.bancaria.domain.port.out.EventLogPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.SugerenciaRepositoryPort;
import com.conciliacion.bancaria.domain.service.MovimientoReversionService;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import com.conciliacion.bancaria.shared.EstadoSugerencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevisionUseCaseImpl — aceptar/rechazar sugerencias")
class RevisionUseCaseImplTest {

    @Mock private SugerenciaRepositoryPort sugerenciaRepo;
    @Mock private MovimientoRepositoryPort movimientoRepo;
    @Mock private EventLogPort eventLog;
    @Mock private PartidaRepositoryPort partidaRepo;

    private RevisionUseCaseImpl useCase;

    private static final Long CONCILIACION_ID = 1L;

    @BeforeEach
    void setUp() {
        MovimientoReversionService reversionService = new MovimientoReversionService(movimientoRepo, partidaRepo);
        useCase = new RevisionUseCaseImpl(sugerenciaRepo, movimientoRepo, eventLog, reversionService);
    }

    private Movimiento movimiento(Long id, EstadoMovimiento estado) {
        return Movimiento.builder()
                .id(id).fecha(LocalDate.of(2026, 6, 1)).monto(new BigDecimal("100"))
                .tipo("DEBITO").descripcion("mov").estado(estado)
                .build();
    }

    private Sugerencia sugerencia(Movimiento bancario, Movimiento contable) {
        return Sugerencia.builder()
                .id(5L).idConciliacion(CONCILIACION_ID)
                .movimientoBancario(bancario).movimientoContable(contable)
                .confianza(new BigDecimal("1.0")).criterio("MONTO_EXACTO")
                .estado(EstadoSugerencia.PENDIENTE_REVISION)
                .build();
    }

    @Test
    @DisplayName("aceptarSugerencia marca ambos movimientos CONCILIADO (comportamiento existente, sin regresión)")
    void aceptarSugerenciaMarcaConciliado() {
        Movimiento bancario = movimiento(20L, EstadoMovimiento.PENDIENTE);
        Movimiento contable = movimiento(10L, EstadoMovimiento.PENDIENTE);
        Sugerencia sug = sugerencia(bancario, contable);

        when(sugerenciaRepo.buscarPorId(5L)).thenReturn(Optional.of(sug));
        when(sugerenciaRepo.actualizar(any())).thenAnswer(inv -> inv.getArgument(0));

        Sugerencia resultado = useCase.aceptarSugerencia(5L, 1L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoSugerencia.ACEPTADA);
        verify(movimientoRepo).actualizar(
                argThat(m -> m.getEstado() == EstadoMovimiento.CONCILIADO), eq(CONCILIACION_ID), eq("BANCARIO"));
        verify(movimientoRepo).actualizar(
                argThat(m -> m.getEstado() == EstadoMovimiento.CONCILIADO), eq(CONCILIACION_ID), eq("CONTABLE"));
    }

    @Test
    @DisplayName("rechazarSugerencia (fix): revierte bancario Y contable a PENDIENTE, no solo la sugerencia")
    void rechazarSugerenciaRevierteAmbosMovimientos() {
        Movimiento bancario = movimiento(20L, EstadoMovimiento.PENDIENTE);
        Movimiento contable = movimiento(10L, EstadoMovimiento.PENDIENTE);
        Sugerencia sug = sugerencia(bancario, contable);

        when(sugerenciaRepo.buscarPorId(5L)).thenReturn(Optional.of(sug));
        when(sugerenciaRepo.actualizar(any())).thenAnswer(inv -> inv.getArgument(0));

        Sugerencia resultado = useCase.rechazarSugerencia(5L, 1L);

        assertThat(resultado.getEstado()).isEqualTo(EstadoSugerencia.RECHAZADA);
        // Antes del fix: ninguna de estas dos llamadas ocurría, dejando los movimientos
        // atascados como si la sugerencia rechazada siguiera vigente.
        verify(movimientoRepo).actualizarEstadoBancario(20L, EstadoMovimiento.PENDIENTE);
        verify(movimientoRepo).actualizarEstadoContable(10L, EstadoMovimiento.PENDIENTE);
        verify(partidaRepo, times(2)).guardar(any());
        verify(eventLog).actionReject(CONCILIACION_ID, 5L, 1L);
    }
}
