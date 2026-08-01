package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.ConciliacionCerradaException;
import com.conciliacion.bancaria.domain.exception.InvalidPeriodTransferException;
import com.conciliacion.bancaria.domain.exception.PartidaPendienteException;
import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("lanzar RecursoNoEncontradoException si la conciliacion no existe")
    void cerrarNoExiste() {
        when(conciliacionRepo.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> closureService.cerrar(99L, 1L, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(RecursoNoEncontradoException.class);
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
    @DisplayName("lanzar RecursoNoEncontradoException si la partida no existe al justificar")
    void justificarNoExiste() {
        when(partidaRepo.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> closureService.justificar(99L, "texto", LocalDate.now()))
                .isInstanceOf(RecursoNoEncontradoException.class);
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

    @Nested
    @DisplayName("cruzarPartidas — tipo CRUZAR")
    class CruzarPartidas {

        private PartidaConciliatoria partida(Long id, Long idMovimiento, String tipoOrigen,
                                              BigDecimal monto, String tipoMovimiento) {
            return PartidaConciliatoria.builder()
                    .id(id).idConciliacion(1L).idMovimiento(idMovimiento)
                    .tipoOrigen(tipoOrigen).estado("PENDIENTE")
                    .montoMovimiento(monto).tipoMovimiento(tipoMovimiento)
                    .build();
        }

        @Test
        @DisplayName("neto cero: cruce completo, sin partida nueva")
        void netoCero() {
            when(partidaRepo.actualizar(any())).thenAnswer(i -> i.getArgument(0));
            when(partidaRepo.buscarPorId(1L)).thenReturn(Optional.of(
                    partida(1L, 10L, "BANCARIO", new BigDecimal("100.00"), "DEBITO")));
            when(partidaRepo.buscarPorId(2L)).thenReturn(Optional.of(
                    partida(2L, 20L, "CONTABLE", new BigDecimal("100.00"), "CREDITO")));

            List<PartidaConciliatoria> resultado =
                    closureService.cruzarPartidas(1L, List.of(2L), "CRUZAR", 1L);

            assertThat(resultado).hasSize(2);
            assertThat(resultado).allSatisfy(p -> {
                assertThat(p.getEstado()).isEqualTo("CRUZADA");
                assertThat(p.getJustificacion()).isEqualTo("Cruzado manualmente");
            });
            verify(partidaRepo, never()).guardar(any());
            verify(movimientoRepo, never()).guardarBancarios(any(), any());
            verify(movimientoRepo, never()).guardarContables(any(), any());
            verify(movimientoRepo).actualizarEstadoBancario(10L, EstadoMovimiento.CONCILIADO);
            verify(movimientoRepo).actualizarEstadoContable(20L, EstadoMovimiento.CONCILIADO);
        }

        @Test
        @DisplayName("neto positivo (origen > destinos): la partida de resto nace del lado del origen")
        void netoPositivoSobraEnOrigen() {
            when(partidaRepo.actualizar(any())).thenAnswer(i -> i.getArgument(0));
            PartidaConciliatoria origen = partida(1L, 10L, "BANCARIO", new BigDecimal("723.00"), "DEBITO");
            PartidaConciliatoria destino = partida(2L, 20L, "CONTABLE", new BigDecimal("500.00"), "CREDITO");
            when(partidaRepo.buscarPorId(1L)).thenReturn(Optional.of(origen));
            when(partidaRepo.buscarPorId(2L)).thenReturn(Optional.of(destino));

            Movimiento sintetico = Movimiento.builder().id(999L)
                    .fecha(LocalDate.now()).descripcion("DIFERENCIA DE CRUCE — partida #1")
                    .monto(new BigDecimal("223.00")).tipo("DEBITO").estado(EstadoMovimiento.PENDIENTE).build();
            when(movimientoRepo.guardarBancarios(any(), eq(1L))).thenReturn(List.of(sintetico));
            when(partidaRepo.guardar(any())).thenAnswer(i -> {
                PartidaConciliatoria p = i.getArgument(0);
                return p.withId(500L);
            });

            List<PartidaConciliatoria> resultado =
                    closureService.cruzarPartidas(1L, List.of(2L), "CRUZAR", 1L);

            // origen + destino + partida de resto
            assertThat(resultado).hasSize(3);
            PartidaConciliatoria resto = resultado.stream()
                    .filter(p -> p.getId().equals(500L)).findFirst().orElseThrow();
            assertThat(resto.getEstado()).isEqualTo("PENDIENTE");
            assertThat(resto.getTipoOrigen()).isEqualTo("BANCARIO");   // mismo lado que origen
            assertThat(resto.getIdMovimiento()).isEqualTo(999L);

            // origen y destino quedan verdaderamente resueltos, no "incompletos"
            resultado.stream().filter(p -> !p.getId().equals(500L)).forEach(p -> {
                assertThat(p.getEstado()).isEqualTo("CRUZADA");
                assertThat(p.getJustificacion()).isEqualTo("Cruzado con diferencia trasladada a partida #500");
                assertThat(p.getJustificacion()).doesNotStartWith("INCOMPLETO");
            });

            // el sintético se creó del lado BANCARIO, por el monto restante, mismo tipo que origen
            var movimientoCaptor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
            verify(movimientoRepo).guardarBancarios(movimientoCaptor.capture(), eq(1L));
            Movimiento creado = (Movimiento) movimientoCaptor.getValue().get(0);
            assertThat(creado.getMonto()).isEqualByComparingTo(new BigDecimal("223.00"));
            assertThat(creado.getTipo()).isEqualTo("DEBITO");
            verify(movimientoRepo, never()).guardarContables(any(), any());

            verify(movimientoRepo).actualizarEstadoBancario(10L, EstadoMovimiento.CONCILIADO);
            verify(movimientoRepo).actualizarEstadoContable(20L, EstadoMovimiento.CONCILIADO);
        }

        @Test
        @DisplayName("neto negativo (destinos > origen): la partida de resto nace del lado contrario al origen")
        void netoNegativoSobraEnDestino() {
            when(partidaRepo.actualizar(any())).thenAnswer(i -> i.getArgument(0));
            PartidaConciliatoria origen = partida(1L, 10L, "BANCARIO", new BigDecimal("500.00"), "DEBITO");
            PartidaConciliatoria destino = partida(2L, 20L, "CONTABLE", new BigDecimal("723.00"), "CREDITO");
            when(partidaRepo.buscarPorId(1L)).thenReturn(Optional.of(origen));
            when(partidaRepo.buscarPorId(2L)).thenReturn(Optional.of(destino));

            Movimiento sintetico = Movimiento.builder().id(998L)
                    .fecha(LocalDate.now()).descripcion("DIFERENCIA DE CRUCE — partida #1")
                    .monto(new BigDecimal("223.00")).tipo("CREDITO").estado(EstadoMovimiento.PENDIENTE).build();
            when(movimientoRepo.guardarContables(any(), eq(1L))).thenReturn(List.of(sintetico));
            when(partidaRepo.guardar(any())).thenAnswer(i -> {
                PartidaConciliatoria p = i.getArgument(0);
                return p.withId(501L);
            });

            List<PartidaConciliatoria> resultado =
                    closureService.cruzarPartidas(1L, List.of(2L), "CRUZAR", 1L);

            PartidaConciliatoria resto = resultado.stream()
                    .filter(p -> p.getId().equals(501L)).findFirst().orElseThrow();
            assertThat(resto.getEstado()).isEqualTo("PENDIENTE");
            // origen es BANCARIO -> el sobrante es del lado contrario: CONTABLE
            assertThat(resto.getTipoOrigen()).isEqualTo("CONTABLE");
            assertThat(resto.getIdMovimiento()).isEqualTo(998L);

            var movimientoCaptor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
            verify(movimientoRepo).guardarContables(movimientoCaptor.capture(), eq(1L));
            Movimiento creado = (Movimiento) movimientoCaptor.getValue().get(0);
            assertThat(creado.getMonto()).isEqualByComparingTo(new BigDecimal("223.00"));
            // origen era DEBITO -> el resto, del lado contrario, es CREDITO
            assertThat(creado.getTipo()).isEqualTo("CREDITO");
            verify(movimientoRepo, never()).guardarBancarios(any(), any());
        }

        @Test
        @DisplayName("partida no encontrada lanza RecursoNoEncontradoException")
        void partidaNoEncontrada() {
            when(partidaRepo.buscarPorId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> closureService.cruzarPartidas(99L, List.of(), "CRUZAR", 1L))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }
}