package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;
import com.conciliacion.bancaria.domain.model.Sugerencia;
import com.conciliacion.bancaria.domain.port.out.*;
import com.conciliacion.bancaria.domain.service.ConciliationEngine;
import com.conciliacion.bancaria.domain.service.CsvValidatorService;
import com.conciliacion.bancaria.domain.service.MovimientoReversionService;
import com.conciliacion.bancaria.domain.service.SiesaXlsParserService;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import com.conciliacion.bancaria.shared.EstadoSugerencia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Solo mockea los puertos (interfaces) — mockear clases concretas
 * (ConciliationEngine, CsvValidatorService, etc.) no funciona en este entorno
 * (JDK 25 + Byte Buddy, ver plan). Se instancian reales.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CargaCsvUseCaseImpl — recarga incremental y reversión del auxiliar")
class CargaCsvUseCaseImplTest {

    @Mock private MovimientoRepositoryPort movimientoRepo;
    @Mock private SugerenciaRepositoryPort sugerenciaRepo;
    @Mock private PartidaRepositoryPort partidaRepo;
    @Mock private JobRepositoryPort jobRepo;
    @Mock private EventLogPort eventLog;
    @Mock private ConciliacionRepositoryPort conciliacionRepo;
    @Mock private ConfiguracionGastoBancarioRepositoryPort gastoRepo;
    @Mock private ConfiguracionExtractoRepositoryPort configuracionExtractoRepo;
    @Mock private BankStatementParserPort bankStatementParserPort;
    @Mock private ConfiguracionExtractoCodec configuracionExtractoCodec;

    private CargaCsvUseCaseImpl useCase;

    private static final Long CONCILIACION_ID = 1L;

    @BeforeEach
    void setUp() {
        MovimientoReversionService reversionService =
                new MovimientoReversionService(movimientoRepo, partidaRepo);
        useCase = new CargaCsvUseCaseImpl(
                new CsvValidatorService(),
                new SiesaXlsParserService(),
                bankStatementParserPort,
                configuracionExtractoCodec,
                new ConciliationEngine(),
                movimientoRepo, sugerenciaRepo, partidaRepo, jobRepo, eventLog,
                conciliacionRepo, gastoRepo, configuracionExtractoRepo, reversionService);
        // Fuera de un contenedor Spring no hay proxy AOP que resuelva @Autowired/@Lazy;
        // se apunta manualmente a la misma instancia para que las llamadas internas vía
        // "self" (necesarias para que @Async no se auto-invoque sin pasar por el proxy
        // en producción) sigan funcionando en el test.
        useCase.self = useCase;
    }

    private Movimiento contable(Long id, LocalDate fecha, String monto, String tipo,
                                String descripcion, EstadoMovimiento estado) {
        return Movimiento.builder()
                .id(id).fecha(fecha).monto(new BigDecimal(monto)).tipo(tipo)
                .descripcion(descripcion).estado(estado)
                .build();
    }

    private Movimiento contableConComprobante(Long id, String numeroComprobante, String monto, String tipo,
                                               EstadoMovimiento estado) {
        return Movimiento.builder()
                .id(id).fecha(LocalDate.of(2026, 6, 1)).monto(new BigDecimal(monto)).tipo(tipo)
                .descripcion("Nota SIESA").estado(estado).numeroComprobante(numeroComprobante)
                .build();
    }

    private Movimiento bancario(Long id) {
        return Movimiento.builder()
                .id(id).fecha(LocalDate.of(2026, 6, 1)).monto(new BigDecimal("100"))
                .tipo("DEBITO").descripcion("bancario").estado(EstadoMovimiento.PENDIENTE)
                .build();
    }

    private Sugerencia sugerenciaActiva(Movimiento bancario, Movimiento contable, EstadoSugerencia estado) {
        return Sugerencia.builder()
                .id(99L).idConciliacion(CONCILIACION_ID)
                .movimientoBancario(bancario).movimientoContable(contable)
                .confianza(new BigDecimal("1.0")).criterio("MONTO_EXACTO")
                .estado(estado)
                .build();
    }

    /** Deja al motor incremental en su rama más simple: sin bancarios libres. */
    private void sinBancariosLibres() {
        when(movimientoRepo.buscarBancariosPendientesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of());
        when(sugerenciaRepo.buscarBancarioIdsConSugerenciaPendiente(CONCILIACION_ID)).thenReturn(Set.of());
        when(sugerenciaRepo.buscarBancarioIdsConSugerenciaActiva(CONCILIACION_ID)).thenReturn(Set.of());
        when(sugerenciaRepo.buscarContableIdsConSugerenciaActiva(CONCILIACION_ID)).thenReturn(Set.of());
    }

    @Nested
    @DisplayName("Contable desaparecido")
    class ContableDesaparecido {

        @Test
        @DisplayName("PENDIENTE sin sugerencia activa: se elimina directo, sin tocar sugerencias ni bancario")
        void pendienteSinSugerenciaActiva() {
            Movimiento c1 = contable(10L, LocalDate.of(2026, 6, 1), "500", "DEBITO", "Nota", EstadoMovimiento.PENDIENTE);
            when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of(c1));
            when(sugerenciaRepo.buscarActivaPorMovimientoContable(10L)).thenReturn(Optional.empty());

            ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of());

            assertThat(resumen.getAnulados()).isEqualTo(1);
            assertThat(resumen.getRevertidos()).isEqualTo(0);
            assertThat(resumen.getJobId()).isNull();
            verify(movimientoRepo).eliminarContablesPorIds(List.of(10L));
            verify(sugerenciaRepo, never()).eliminarPorId(any());
            verify(movimientoRepo, never()).actualizarEstadoBancario(any(), any());
        }

        @Test
        @DisplayName("PENDIENTE con sugerencia PENDIENTE_REVISION activa: se revierte el bancario y se elimina el contable")
        void pendienteConSugerenciaActiva() {
            Movimiento b1 = bancario(20L);
            Movimiento c1 = contable(10L, LocalDate.of(2026, 6, 1), "500", "DEBITO", "Nota", EstadoMovimiento.PENDIENTE);
            Sugerencia sug = sugerenciaActiva(b1, c1, EstadoSugerencia.PENDIENTE_REVISION);

            when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of(c1));
            when(sugerenciaRepo.buscarActivaPorMovimientoContable(10L)).thenReturn(Optional.of(sug));

            ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of());

            assertThat(resumen.getAnulados()).isEqualTo(1);
            assertThat(resumen.getRevertidos()).isEqualTo(1);
            verify(sugerenciaRepo).eliminarPorId(99L);
            verify(movimientoRepo).actualizarEstadoBancario(20L, EstadoMovimiento.PENDIENTE);
            verify(partidaRepo).guardar(any());
            verify(movimientoRepo).eliminarContablesPorIds(List.of(10L));
            verify(eventLog).contableRevertido(eq(CONCILIACION_ID), eq(10L), any(), any(), any(), any(),
                    eq(20L), eq("PENDIENTE_REVISION"));
        }

        @Test
        @DisplayName("CONCILIADO (sugerencia ACEPTADA): igual se revierte — 'ya aprobado' no lo protege")
        void conciliadoConSugerenciaAceptada() {
            Movimiento b1 = bancario(20L);
            Movimiento c1 = contable(10L, LocalDate.of(2026, 6, 1), "500", "DEBITO", "Nota", EstadoMovimiento.CONCILIADO);
            Sugerencia sug = sugerenciaActiva(b1, c1, EstadoSugerencia.ACEPTADA);

            when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of(c1));
            when(sugerenciaRepo.buscarActivaPorMovimientoContable(10L)).thenReturn(Optional.of(sug));

            ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of());

            assertThat(resumen.getRevertidos()).isEqualTo(1);
            verify(sugerenciaRepo).eliminarPorId(99L);
            verify(movimientoRepo).actualizarEstadoBancario(20L, EstadoMovimiento.PENDIENTE);
            verify(movimientoRepo).eliminarContablesPorIds(List.of(10L));
            verify(eventLog).contableRevertido(eq(CONCILIACION_ID), eq(10L), any(), any(), any(), any(),
                    eq(20L), eq("ACEPTADA"));
        }
    }

    @Test
    @DisplayName("Huella repetida: 2 existentes, 1 en archivo nuevo -> exactamente 1 se da de baja, prioriza el sin sugerencia")
    void huellaRepetidaPriorizaSinSugerencia() {
        LocalDate fecha = LocalDate.of(2026, 6, 1);
        Movimiento c1SinSugerencia = contable(10L, fecha, "500", "DEBITO", "IMPUESTO 4X1000", EstadoMovimiento.PENDIENTE);
        Movimiento c2ConSugerencia = contable(11L, fecha, "500", "DEBITO", "IMPUESTO 4X1000", EstadoMovimiento.PENDIENTE);
        Movimiento archivoNuevo = contable(null, fecha, "500", "DEBITO", "IMPUESTO 4X1000", EstadoMovimiento.PENDIENTE);

        when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID))
                .thenReturn(List.of(c1SinSugerencia, c2ConSugerencia));
        when(sugerenciaRepo.buscarActivaPorMovimientoContable(10L)).thenReturn(Optional.empty());
        when(sugerenciaRepo.buscarActivaPorMovimientoContable(11L))
                .thenReturn(Optional.of(sugerenciaActiva(bancario(20L), c2ConSugerencia, EstadoSugerencia.ACEPTADA)));

        ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of(archivoNuevo));

        assertThat(resumen.getNuevos()).isEqualTo(0);
        assertThat(resumen.getAnulados()).isEqualTo(1);
        assertThat(resumen.getRevertidos()).isEqualTo(0); // se dio de baja el que NO tenia sugerencia
        verify(movimientoRepo).eliminarContablesPorIds(List.of(10L));
        verify(sugerenciaRepo, never()).eliminarPorId(any());
    }

    @Test
    @DisplayName("Renglon nuevo sin ningun desaparecido: comportamiento actual intacto")
    void renglonNuevoSinDesaparecidos() {
        Movimiento nuevo = contable(null, LocalDate.of(2026, 6, 2), "300", "CREDITO", "Nuevo pago", EstadoMovimiento.PENDIENTE);
        when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of());
        when(movimientoRepo.guardarContables(anyList(), eq(CONCILIACION_ID))).thenReturn(List.of(nuevo.withId(50L)));
        when(jobRepo.crearJob(CONCILIACION_ID)).thenReturn("job-1");
        sinBancariosLibres();

        ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of(nuevo));

        assertThat(resumen.getNuevos()).isEqualTo(1);
        assertThat(resumen.getAnulados()).isEqualTo(0);
        assertThat(resumen.getJobId()).isEqualTo("job-1");
        verify(movimientoRepo).guardarContables(anyList(), eq(CONCILIACION_ID));
        verify(jobRepo).completar("job-1");
    }

    @Test
    @DisplayName("Caso mixto: nuevos + anulados en la misma carga")
    void casoMixtoNuevosYAnulados() {
        Movimiento b1 = bancario(20L);
        Movimiento cViejo = contable(10L, LocalDate.of(2026, 6, 1), "500", "DEBITO", "Viejo", EstadoMovimiento.CONCILIADO);
        Movimiento cNuevo = contable(null, LocalDate.of(2026, 6, 3), "700", "CREDITO", "Nuevo", EstadoMovimiento.PENDIENTE);

        when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of(cViejo));
        when(sugerenciaRepo.buscarActivaPorMovimientoContable(10L))
                .thenReturn(Optional.of(sugerenciaActiva(b1, cViejo, EstadoSugerencia.ACEPTADA)));
        when(movimientoRepo.guardarContables(anyList(), eq(CONCILIACION_ID))).thenReturn(List.of(cNuevo.withId(51L)));
        when(jobRepo.crearJob(CONCILIACION_ID)).thenReturn("job-2");
        sinBancariosLibres();

        ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of(cNuevo));

        assertThat(resumen.getNuevos()).isEqualTo(1);
        assertThat(resumen.getAnulados()).isEqualTo(1);
        assertThat(resumen.getRevertidos()).isEqualTo(1);
        verify(movimientoRepo).actualizarEstadoBancario(20L, EstadoMovimiento.PENDIENTE);
        verify(movimientoRepo).guardarContables(anyList(), eq(CONCILIACION_ID));
    }

    @Nested
    @DisplayName("Huella con numeroComprobante — bug real: mismo comprobante reutilizado, monto distinto")
    class HuellaConComprobante {

        @Test
        @DisplayName("mismo comprobante pero monto distinto: el viejo CONCILIADO se revierte, la fila nueva entra como nueva")
        void mismoComprobanteMontoDistintoRevierteYAgregaNuevo() {
            // Caso real: contable CONCILIADO con comprobante "1001" y monto 500 (ya aprobado).
            // Se anula en el sistema contable y se reingresa con el MISMO comprobante pero
            // monto 300. Antes del fix, huella()="NC:1001" ignoraba el monto y el diff por
            // multiconjunto veía "1 existente, 1 en archivo nuevo" con la misma huella -> el
            // viejo nunca se marcaba desaparecido y la reversión nunca se disparaba.
            Movimiento bancario = bancario(20L);
            Movimiento viejoConciliado = contableConComprobante(10L, "1001", "500", "DEBITO", EstadoMovimiento.CONCILIADO);
            Movimiento reingresoMenorValor = contableConComprobante(null, "1001", "300", "DEBITO", EstadoMovimiento.PENDIENTE);

            when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of(viejoConciliado));
            when(sugerenciaRepo.buscarActivaPorMovimientoContable(10L))
                    .thenReturn(Optional.of(sugerenciaActiva(bancario, viejoConciliado, EstadoSugerencia.ACEPTADA)));
            when(movimientoRepo.guardarContables(anyList(), eq(CONCILIACION_ID)))
                    .thenReturn(List.of(reingresoMenorValor.withId(60L)));
            when(jobRepo.crearJob(CONCILIACION_ID)).thenReturn("job-3");
            sinBancariosLibres();

            ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of(reingresoMenorValor));

            assertThat(resumen.getNuevos()).isEqualTo(1);
            assertThat(resumen.getAnulados()).isEqualTo(1);
            assertThat(resumen.getRevertidos()).isEqualTo(1);
            verify(sugerenciaRepo).eliminarPorId(99L);
            verify(movimientoRepo).actualizarEstadoBancario(20L, EstadoMovimiento.PENDIENTE);
            verify(movimientoRepo).eliminarContablesPorIds(List.of(10L));
            verify(movimientoRepo).guardarContables(anyList(), eq(CONCILIACION_ID));
        }

        @Test
        @DisplayName("caso feliz de control: mismo comprobante y mismo monto/tipo en la re-exportacion -> no es nuevo ni desaparecido")
        void mismoComprobanteMismoMontoNoEsNuevoNiDesaparecido() {
            Movimiento existente = contableConComprobante(10L, "2002", "800", "CREDITO", EstadoMovimiento.PENDIENTE);
            Movimiento reexportadoIgual = contableConComprobante(null, "2002", "800", "CREDITO", EstadoMovimiento.PENDIENTE);

            when(movimientoRepo.buscarTodosContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of(existente));

            ResumenCargaAuxiliar resumen = useCase.procesarRecargaAuxiliar(CONCILIACION_ID, List.of(reexportadoIgual));

            assertThat(resumen.getNuevos()).isEqualTo(0);
            assertThat(resumen.getAnulados()).isEqualTo(0);
            assertThat(resumen.getRevertidos()).isEqualTo(0);
            verify(movimientoRepo, never()).eliminarContablesPorIds(any());
            verify(movimientoRepo, never()).guardarContables(any(), any());
        }
    }

    @Nested
    @DisplayName("Race entre ejecutarMotorAsync y ejecutarMotorIncrementalAsync (bug real: 328 pendientes fantasma)")
    class RaceEntreMotores {

        private List<Movimiento> bancarios;
        private List<Movimiento> contables;
        private List<Long> idsBancariosConMatch;

        @BeforeEach
        void prepararDatos() {
            // 8 bancarios con cruce (mismo monto/tipo/fecha que sus contables) + 2 genuinamente
            // huérfanos (monto sin par) — misma proporción "muchos matches, pocos huérfanos" del
            // caso real reportado (328 bancarios, 321 sugerencias, 7 huérfanos).
            bancarios = new ArrayList<>();
            idsBancariosConMatch = new ArrayList<>();
            for (long id = 101; id <= 108; id++) {
                bancarios.add(Movimiento.builder().id(id).fecha(LocalDate.of(2026, 6, 1))
                        .monto(new BigDecimal("100")).tipo("DEBITO").descripcion("bancario " + id)
                        .estado(EstadoMovimiento.PENDIENTE).build());
                idsBancariosConMatch.add(id);
            }
            bancarios.add(Movimiento.builder().id(109L).fecha(LocalDate.of(2026, 6, 1))
                    .monto(new BigDecimal("999")).tipo("DEBITO").descripcion("huerfano 1")
                    .estado(EstadoMovimiento.PENDIENTE).build());
            bancarios.add(Movimiento.builder().id(110L).fecha(LocalDate.of(2026, 6, 1))
                    .monto(new BigDecimal("998")).tipo("DEBITO").descripcion("huerfano 2")
                    .estado(EstadoMovimiento.PENDIENTE).build());

            contables = new ArrayList<>();
            for (long id = 201; id <= 208; id++) {
                contables.add(Movimiento.builder().id(id).fecha(LocalDate.of(2026, 6, 1))
                        .monto(new BigDecimal("100")).tipo("DEBITO").descripcion("contable " + id)
                        .estado(EstadoMovimiento.PENDIENTE).build());
            }
        }

        private boolean partidaBancarioPresente(
                List<com.conciliacion.bancaria.domain.model.PartidaConciliatoria> partidas, Long idMovimiento) {
            return partidas.stream().anyMatch(p ->
                    idMovimiento.equals(p.getIdMovimiento()) && "BANCARIO".equals(p.getTipoOrigen()));
        }

        @Test
        @DisplayName("orden problemático (auxiliar cruza y limpia antes de que el extracto inserte sus pendientes stale) -> la reconciliación final los limpia igual")
        void ordenProblematicoQuedaReconciliado() {
            // --- Job B (auxiliar): corre PRIMERO, cruza los 8 contables contra los 8 bancarios ---
            when(movimientoRepo.buscarBancariosPendientesPorConciliacion(CONCILIACION_ID)).thenReturn(bancarios);
            when(sugerenciaRepo.buscarBancarioIdsConSugerenciaPendiente(CONCILIACION_ID)).thenReturn(Set.of());
            // La reconciliación al final de CADA job consulta el estado "actual" de sugerencias
            // activas: en este escenario las 8 sugerencias del auxiliar ya existen para cuando
            // cualquiera de los dos jobs llega a su propio paso de limpieza.
            when(sugerenciaRepo.buscarBancarioIdsConSugerenciaActiva(CONCILIACION_ID))
                    .thenReturn(new java.util.HashSet<>(idsBancariosConMatch));
            when(sugerenciaRepo.buscarContableIdsConSugerenciaActiva(CONCILIACION_ID)).thenReturn(Set.of());

            useCase.ejecutarMotorIncrementalAsync("job-aux", CONCILIACION_ID, contables);

            // Job B generó las 8 sugerencias correctamente.
            verify(sugerenciaRepo).guardarTodas(argThat(list -> list.size() == 8));
            // E intentó limpiar la partida pendiente de cada bancario emparejado dos veces: una
            // vez por el paso existente (uno por sugerencia recién creada) y otra por la
            // reconciliación final que corre siempre al terminar CUALQUIER job (la del propio
            // job B ya ve sus propias sugerencias recién guardadas). En la BD real, en este punto
            // el extracto (job A) todavía no ha insertado nada, así que ninguno de los dos
            // intentos encontraría fila alguna que borrar (aquí son solo invocaciones a un mock).
            idsBancariosConMatch.forEach(id ->
                    verify(partidaRepo, times(2)).eliminarPendientePorMovimiento(id, "BANCARIO"));

            // --- Job A (extracto): corre SEGUNDO, con una lectura stale de contables (vacía) ---
            // porque en producción su hilo async puede leer la tabla de contables antes de que el
            // commit del auxiliar sea visible — el mecanismo exacto del bug real reportado.
            when(movimientoRepo.buscarBancariosPorConciliacion(CONCILIACION_ID)).thenReturn(bancarios);
            when(movimientoRepo.buscarContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of());

            useCase.ejecutarMotorAsync("job-extracto", CONCILIACION_ID);

            // Job A, al no ver contables, calculó (incorrectamente) que los 10 bancarios están sin
            // cruce y los guardó TODOS como partida pendiente — incluyendo los 8 que job B ya
            // había resuelto. Esta es la inserción que, antes del fix, quedaba huérfana para
            // siempre (el intento de borrado de job B ya había pasado y no encontró nada).
            verify(partidaRepo).guardarTodas(argThat(partidas ->
                    partidas.size() == 10 && idsBancariosConMatch.stream()
                            .allMatch(id -> partidaBancarioPresente(partidas, id))));

            // Propiedad que debe cumplirse SIEMPRE, sin importar el orden real de los dos jobs:
            // cada bancario con sugerencia recibe una limpieza adicional DESPUÉS de que job A
            // insertó su partida stale — un tercer intento, esta vez desde la reconciliación
            // final de job A, que en la BD real SÍ encuentra y borra la fila recién insertada.
            // Sin el fix, el conteo se habría quedado en 2 (ninguno de los dos intentos de job B
            // encuentra nada, porque corren antes de que job A inserte) y la partida fantasma
            // sobrevive para siempre — exactamente el bug reportado.
            idsBancariosConMatch.forEach(id ->
                    verify(partidaRepo, times(3)).eliminarPendientePorMovimiento(id, "BANCARIO"));

            // Los 2 huérfanos genuinos nunca deben limpiarse — no tienen sugerencia.
            verify(partidaRepo, never()).eliminarPendientePorMovimiento(109L, "BANCARIO");
            verify(partidaRepo, never()).eliminarPendientePorMovimiento(110L, "BANCARIO");
        }
    }

    @Nested
    @DisplayName("Partidas PENDIENTE duplicadas al subir varios extractos (tarjetas de crédito, auxiliar conjunto)")
    class DuplicadosPartidasPendientes {

        private Movimiento bancario(Long id, String descripcion) {
            return Movimiento.builder()
                    .id(id).fecha(LocalDate.of(2026, 6, 1))
                    .monto(new BigDecimal("100")).tipo("DEBITO").descripcion(descripcion)
                    .estado(EstadoMovimiento.PENDIENTE).build();
        }

        @Test
        @DisplayName("3 extractos subidos seguidos sin auxiliar: el bancario del primero termina con 1 sola partida PENDIENTE")
        void tresExtractosSeguidosNoDuplicanPartida() {
            // Simula el estado real de partidas_conciliatorias: el conjunto de ids de
            // movimiento que YA tienen una partida PENDIENTE se actualiza con cada
            // guardarTodas(), tal como lo vería PartidaJpaRepository.findIdsMovimientoConPendiente
            // contra la BD real entre una corrida del motor y la siguiente.
            Set<Long> pendientesExistentes = new HashSet<>();
            when(partidaRepo.buscarIdsMovimientoConPendiente(eq(CONCILIACION_ID), eq("BANCARIO")))
                    .thenAnswer(inv -> new HashSet<>(pendientesExistentes));
            when(partidaRepo.guardarTodas(anyList())).thenAnswer(inv -> {
                List<PartidaConciliatoria> partidas = inv.getArgument(0);
                partidas.stream()
                        .filter(p -> "BANCARIO".equals(p.getTipoOrigen()))
                        .forEach(p -> pendientesExistentes.add(p.getIdMovimiento()));
                return partidas;
            });
            when(sugerenciaRepo.buscarBancarioIdsConSugerenciaActiva(CONCILIACION_ID)).thenReturn(Set.of());
            when(sugerenciaRepo.buscarContableIdsConSugerenciaActiva(CONCILIACION_ID)).thenReturn(Set.of());
            // Sin auxiliar cargado todavía: cero contables en las 3 corridas.
            when(movimientoRepo.buscarContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of());

            Movimiento archivo1 = bancario(101L, "extracto 1");
            Movimiento archivo2 = bancario(102L, "extracto 2");
            Movimiento archivo3 = bancario(103L, "extracto 3");

            // Cada "subida de extracto" relee TODOS los bancarios acumulados de la
            // conciliación (auxiliar_conjunto=true no borra los anteriores) y dispara el
            // motor COMPLETO -- exactamente el flujo real de cargarExtractoBancario.
            when(movimientoRepo.buscarBancariosPorConciliacion(CONCILIACION_ID))
                    .thenReturn(List.of(archivo1));
            useCase.ejecutarMotorAsync("job-1", CONCILIACION_ID);

            when(movimientoRepo.buscarBancariosPorConciliacion(CONCILIACION_ID))
                    .thenReturn(List.of(archivo1, archivo2));
            useCase.ejecutarMotorAsync("job-2", CONCILIACION_ID);

            when(movimientoRepo.buscarBancariosPorConciliacion(CONCILIACION_ID))
                    .thenReturn(List.of(archivo1, archivo2, archivo3));
            useCase.ejecutarMotorAsync("job-3", CONCILIACION_ID);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PartidaConciliatoria>> captor = ArgumentCaptor.forClass(List.class);
            verify(partidaRepo, atLeastOnce()).guardarTodas(captor.capture());

            long vecesGuardadoArchivo1 = captor.getAllValues().stream()
                    .flatMap(List::stream)
                    .filter(p -> "BANCARIO".equals(p.getTipoOrigen()) && archivo1.getId().equals(p.getIdMovimiento()))
                    .count();
            long vecesGuardadoArchivo2 = captor.getAllValues().stream()
                    .flatMap(List::stream)
                    .filter(p -> "BANCARIO".equals(p.getTipoOrigen()) && archivo2.getId().equals(p.getIdMovimiento()))
                    .count();

            // Antes del fix: archivo1 se hubiera guardado 3 veces (una por cada corrida
            // que lo relee todavía PENDIENTE); archivo2, 2 veces. Con el fix, cada
            // movimiento sólo genera su partida PENDIENTE una vez, en la corrida donde
            // apareció por primera vez.
            assertThat(vecesGuardadoArchivo1).as("archivo1 no debe duplicarse en corridas posteriores").isEqualTo(1);
            assertThat(vecesGuardadoArchivo2).as("archivo2 no debe duplicarse en la corrida 3").isEqualTo(1);
            assertThat(pendientesExistentes).containsExactlyInAnyOrder(101L, 102L, 103L);
        }

        @Test
        @DisplayName("sinPendienteExistente no filtra nada si no hay partidas previas (caso normal)")
        void sinPartidasPreviasNoFiltraNada() {
            when(partidaRepo.buscarIdsMovimientoConPendiente(eq(CONCILIACION_ID), any())).thenReturn(Set.of());
            when(sugerenciaRepo.buscarBancarioIdsConSugerenciaActiva(CONCILIACION_ID)).thenReturn(Set.of());
            when(sugerenciaRepo.buscarContableIdsConSugerenciaActiva(CONCILIACION_ID)).thenReturn(Set.of());
            when(movimientoRepo.buscarContablesPorConciliacion(CONCILIACION_ID)).thenReturn(List.of());
            when(movimientoRepo.buscarBancariosPorConciliacion(CONCILIACION_ID))
                    .thenReturn(List.of(bancario(201L, "único")));
            when(partidaRepo.guardarTodas(anyList())).thenAnswer(inv -> inv.getArgument(0));

            useCase.ejecutarMotorAsync("job-1", CONCILIACION_ID);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PartidaConciliatoria>> captor = ArgumentCaptor.forClass(List.class);
            verify(partidaRepo, atLeastOnce()).guardarTodas(captor.capture());
            long veces = captor.getAllValues().stream().flatMap(List::stream)
                    .filter(p -> 201L == p.getIdMovimiento()).count();
            assertThat(veces).isEqualTo(1);
        }
    }
}
