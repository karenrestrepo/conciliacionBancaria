package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.ConciliacionCerradaException;
import com.conciliacion.bancaria.domain.exception.InvalidPeriodTransferException;
import com.conciliacion.bancaria.domain.exception.PartidaPendienteException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClosureService — cierre y justificacion")
class ClosureServiceTest {

    @Mock private ConciliacionRepositoryPort conciliacionRepo;
    @Mock private PartidaRepositoryPort partidaRepo;
    @Mock private MovimientoRepositoryPort movimientoRepo;
    @Mock private ConfiguracionGastoBancarioRepositoryPort gastoRepo;

    private ClosureService closureService;

    @BeforeEach
    void setUp() {
        closureService = new ClosureService(conciliacionRepo, partidaRepo, movimientoRepo, gastoRepo);
    }

    private Conciliacion enEstado(EstadoConciliacion estado) {
        return Conciliacion.builder()
                .id(1L).periodo("2024-01").estado(estado).idUsuarioCreador(1L).build();
    }

    private PartidaConciliatoria partidaPendiente() {
        return PartidaConciliatoria.builder()
                .id(1L).idConciliacion(1L).idMovimiento(1L)
                .tipoOrigen("BANCARIO").estado("PENDIENTE").build();
    }

    @Test
    @DisplayName("cerrar correctamente cuando no hay partidas pendientes")
    void cerrarSinPartidas() {
        when(conciliacionRepo.buscarPorId(1L)).thenReturn(Optional.of(enEstado(EstadoConciliacion.EN_REVISION)));
        when(partidaRepo.buscarPendientesSinJustificar(1L)).thenReturn(List.of());
        when(conciliacionRepo.guardar(any())).thenAnswer(i -> i.getArgument(0));

        Conciliacion resultado = closureService.cerrar(1L, 2L,
                new BigDecimal("1000.00"), new BigDecimal("950.00"));

        assertThat(resultado.getEstado()).isEqualTo(EstadoConciliacion.CERRADA);
        assertThat(resultado.getDiferenciaSaldo()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("lanzar PartidaPendienteException si hay partidas sin justificar")
    void cerrarConPartidasPendientes() {
        when(conciliacionRepo.buscarPorId(1L)).thenReturn(Optional.of(enEstado(EstadoConciliacion.EN_REVISION)));
        when(partidaRepo.buscarPendientesSinJustificar(1L)).thenReturn(List.of(partidaPendiente()));

        assertThatThrownBy(() -> closureService.cerrar(1L, 2L, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(PartidaPendienteException.class);
    }

    @Test
    @DisplayName("lanzar ConciliacionCerradaException si ya esta cerrada")
    void cerrarYaCerrada() {
        when(conciliacionRepo.buscarPorId(1L)).thenReturn(Optional.of(enEstado(EstadoConciliacion.CERRADA)));

        assertThatThrownBy(() -> closureService.cerrar(1L, 2L, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(ConciliacionCerradaException.class);
    }

    @Test
    @DisplayName("lanzar IllegalArgumentException si la conciliacion no existe")
    void cerrarNoExiste() {
        when(conciliacionRepo.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> closureService.cerrar(99L, 1L, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("justificar partida correctamente")
    void justificarPartida() {
        when(partidaRepo.buscarPorId(1L)).thenReturn(Optional.of(partidaPendiente()));
        when(partidaRepo.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        PartidaConciliatoria resultado = closureService.justificar(
                1L, "Comision bancaria", LocalDate.of(2024, 1, 31));

        assertThat(resultado.getEstado()).isEqualTo("JUSTIFICADA");
        assertThat(resultado.getJustificacion()).isEqualTo("Comision bancaria");
        assertThat(resultado.getFechaJustificacion()).isEqualTo(LocalDate.of(2024, 1, 31));
    }

    @Test
    @DisplayName("lanzar IllegalArgumentException si la partida no existe al justificar")
    void justificarNoExiste() {
        when(partidaRepo.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> closureService.justificar(99L, "texto", LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("arrastrarPartida a periodo abierto funciona correctamente")
    void arrastrarPeriodoAbierto() {
        when(partidaRepo.buscarPorId(1L)).thenReturn(Optional.of(partidaPendiente()));
        when(conciliacionRepo.buscarPorPeriodo("2024-02"))
                .thenReturn(Optional.of(enEstado(EstadoConciliacion.BORRADOR)));
        when(partidaRepo.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        PartidaConciliatoria resultado = closureService.arrastrarPartida(1L, "2024-02", 1L);

        assertThat(resultado.getEstado()).isEqualTo("ARRASTRADA");
        assertThat(resultado.getPeriodoArrastre()).isEqualTo("2024-02");
    }

    @Test
    @DisplayName("arrastrarPartida lanza excepcion si periodo destino esta cerrado")
    void arrastrarPeriodoCerrado() {
        when(partidaRepo.buscarPorId(1L)).thenReturn(Optional.of(partidaPendiente()));
        when(conciliacionRepo.buscarPorPeriodo("2024-02"))
                .thenReturn(Optional.of(enEstado(EstadoConciliacion.CERRADA)));

        assertThatThrownBy(() -> closureService.arrastrarPartida(1L, "2024-02", 1L))
                .isInstanceOf(InvalidPeriodTransferException.class);
    }

    @Test
    @DisplayName("arrastrarPartida a periodo inexistente tambien funciona")
    void arrastrarPeriodoInexistente() {
        when(partidaRepo.buscarPorId(1L)).thenReturn(Optional.of(partidaPendiente()));
        when(conciliacionRepo.buscarPorPeriodo("2025-01")).thenReturn(Optional.empty());
        when(partidaRepo.actualizar(any())).thenAnswer(i -> i.getArgument(0));

        PartidaConciliatoria resultado = closureService.arrastrarPartida(1L, "2025-01", 1L);

        assertThat(resultado.getEstado()).isEqualTo("ARRASTRADA");
    }
}