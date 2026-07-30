package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.Movimiento;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
}
