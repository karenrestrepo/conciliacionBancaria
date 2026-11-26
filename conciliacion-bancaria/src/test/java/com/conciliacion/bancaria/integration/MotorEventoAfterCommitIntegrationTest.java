package com.conciliacion.bancaria.integration;

import com.conciliacion.bancaria.adapter.out.persistence.repository.ConciliacionJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoBancarioJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoContableJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.PartidaJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.SugerenciaJpaRepository;
import com.conciliacion.bancaria.application.usecase.CargaCsvUseCaseImpl;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.port.in.JobStatusUseCase.JobStatus;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.JobRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Prueba estructural del fix a la condición de carrera transacción/async (ver
 * {@code CargaCsvUseCaseImpl}, comentario sobre {@code self} y
 * {@link com.conciliacion.bancaria.application.usecase.event.MotorCompletoRequestedEvent}):
 * el motor de conciliación NUNCA debe empezar a leer datos antes de que la transacción que lo
 * disparó haya hecho commit. Usa {@code reprocesarMotor} porque publica exactamente el mismo
 * evento/listener que {@code cargarExtractoBancario} (el que causó el bug real, conciliación
 * #29) pero sin necesitar parsear un archivo real ni configurar un {@code ConfiguracionExtracto}
 * -- solo relee bancarios/contables ya guardados, así que basta con guardar uno antes.
 *
 * No se basa en timing real (el hilo {@code @Async} podría "ganar la carrera" de casualidad
 * incluso con el bug presente, si el thread pool tarda más que el resto del método
 * transaccional en arrancar). En su lugar, envuelve la llamada al caso de uso en una
 * transacción propia ({@link TransactionTemplate}): como {@code reprocesarMotor} es
 * {@code @Transactional} con propagación REQUIRED, se UNE a esa transacción externa en vez de
 * abrir la suya propia -- así que mientras el bloque de {@code TransactionTemplate} sigue
 * ejecutándose, la transacción real todavía NO ha hecho commit, sin importar cuánto tiempo
 * pase. El listener {@code @TransactionalEventListener(phase = AFTER_COMMIT)} solo puede
 * disparar el motor una vez que ese commit ocurre -- es una garantía de Spring, no una
 * cuestión de suerte -- así que si el motor arrancara antes (el bug original), lo haría
 * mientras el bloque sigue activo, y el hook de {@code jobRepo} lo detectaría de forma
 * determinista.
 *
 * {@code jobRepo} se reemplaza por completo ({@code @MockitoBean}, no {@code @MockitoSpyBean})
 * con un fake en memoria en vez de espiar el bean real -- espiar necesitaría que Byte Buddy
 * subclasee {@code JobRepositoryAdapter} (la clase concreta), justo lo que falla en este
 * entorno (JDK 25, ver el mismo problema ya documentado en
 * {@code MotorAsyncRollbackIntegrationTest}). Reemplazar la interfaz completa no tiene ese
 * problema y de paso da control total para detectar cuándo arranca el motor.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integración — el motor solo arranca después del commit (fix condición de carrera transacción/async)")
class MotorEventoAfterCommitIntegrationTest {

    @Autowired
    private CargaCsvUseCaseImpl cargaCsvUseCase;
    @Autowired
    private ConciliacionRepositoryPort conciliacionRepo;
    @Autowired
    private MovimientoRepositoryPort movimientoRepo;
    @Autowired
    private MovimientoBancarioJpaRepository movimientoBancarioJpaRepo;
    @Autowired
    private MovimientoContableJpaRepository movimientoContableJpaRepo;
    @Autowired
    private SugerenciaJpaRepository sugerenciaJpaRepo;
    @Autowired
    private PartidaJpaRepository partidaJpaRepo;
    @Autowired
    private ConciliacionJpaRepository conciliacionJpaRepo;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private JobRepositoryPort jobRepo;

    private final Map<String, JobStatus> jobsFake = new ConcurrentHashMap<>();

    private Long idConciliacion;

    @BeforeEach
    void setUp() {
        String periodo = "2096-" + String.format("%02d", 1 + (int) (Math.random() * 9));
        idConciliacion = conciliacionRepo.guardar(Conciliacion.builder()
                .periodo(periodo)
                .idCuenta(1L)
                .estado(EstadoConciliacion.BORRADOR)
                .idUsuarioCreador(1L)
                .build()).getId();

        // Fake mínimo de JobRepositoryPort en memoria -- ver el porqué en el Javadoc de la clase.
        when(jobRepo.crearJob(anyLong())).thenAnswer(inv -> {
            String jobId = UUID.randomUUID().toString();
            jobsFake.put(jobId, new JobStatus(jobId, "PENDING", 0, null));
            return jobId;
        });
        doAnswer(inv -> {
            String jobId = inv.getArgument(0);
            int progreso = inv.getArgument(1);
            jobsFake.computeIfPresent(jobId, (id, s) -> new JobStatus(id, "IN_PROGRESS", progreso, null));
            return null;
        }).when(jobRepo).actualizarProgreso(anyString(), anyInt());
        doAnswer(inv -> {
            String jobId = inv.getArgument(0);
            jobsFake.computeIfPresent(jobId, (id, s) -> new JobStatus(id, "COMPLETED", 100, null));
            return null;
        }).when(jobRepo).completar(anyString());
        doAnswer(inv -> {
            String jobId = inv.getArgument(0);
            String mensaje = inv.getArgument(1);
            jobsFake.computeIfPresent(jobId, (id, s) -> new JobStatus(id, "FAILED", s.progreso(), mensaje));
            return null;
        }).when(jobRepo).fallar(anyString(), anyString());
        when(jobRepo.obtener(anyString())).thenAnswer(inv -> jobsFake.get((String) inv.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            partidaJpaRepo.deleteByIdConciliacion(idConciliacion);
            sugerenciaJpaRepo.deleteByIdConciliacion(idConciliacion);
            movimientoBancarioJpaRepo.deleteAll(movimientoBancarioJpaRepo.findByIdConciliacion(idConciliacion));
            movimientoContableJpaRepo.deleteAll(movimientoContableJpaRepo.findByIdConciliacion(idConciliacion));
            conciliacionJpaRepo.deleteById(idConciliacion);
        });
    }

    private Movimiento movimiento(BigDecimal monto, String descripcion) {
        return Movimiento.builder()
                .fecha(LocalDate.of(2026, 6, 1))
                .monto(monto)
                .tipo("DEBITO")
                .descripcion(descripcion)
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }

    private JobStatus esperarJobTerminado(String jobId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            JobStatus jobStatus = jobRepo.obtener(jobId);
            if (jobStatus != null && ("COMPLETED".equals(jobStatus.estado()) || "FAILED".equals(jobStatus.estado()))) {
                return jobStatus;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Job " + jobId + " no terminó dentro del tiempo esperado");
    }

    @Test
    @DisplayName("el motor no arranca mientras la transacción externa de reprocesarMotor sigue activa, y sí corre después del commit")
    void motorSoloArrancaDespuesDelCommit() throws Exception {
        List<Movimiento> bancarios = movimientoRepo.guardarBancarios(
                List.of(movimiento(new BigDecimal("50.00"), "bancario real")), idConciliacion);
        assertThat(bancarios).hasSize(1);

        AtomicBoolean transaccionExternaSigueActiva = new AtomicBoolean(true);
        AtomicBoolean motorArrancoMientrasTransaccionActiva = new AtomicBoolean(false);
        AtomicInteger vecesQueArranco = new AtomicInteger(0);

        // El primer paso real de CUALQUIERA de los dos motores (ejecutarMotorAsync /
        // ejecutarMotorIncrementalAsync) es jobRepo.actualizarProgreso(jobId, 10). Si el
        // motor arranca antes de tiempo (el bug), este hook lo vería mientras
        // transaccionExternaSigueActiva todavía es true. Se agrega DESPUÉS del stub base de
        // setUp() para envolverlo, no reemplazarlo -- así el fake sigue funcionando.
        doAnswer(inv -> {
            int progreso = inv.getArgument(1);
            if (progreso == 10) {
                vecesQueArranco.incrementAndGet();
                if (transaccionExternaSigueActiva.get()) {
                    motorArrancoMientrasTransaccionActiva.set(true);
                }
            }
            String jobId = inv.getArgument(0);
            jobsFake.computeIfPresent(jobId, (id, s) -> new JobStatus(id, "IN_PROGRESS", progreso, null));
            return null;
        }).when(jobRepo).actualizarProgreso(anyString(), anyInt());

        String[] jobIdHolder = new String[1];

        // reprocesarMotor es @Transactional (REQUIRED): al llamarlo desde DENTRO de este
        // TransactionTemplate, se UNE a esta transacción en vez de abrir la suya propia -- el
        // commit real solo ocurre cuando este bloque termina, no cuando reprocesarMotor(...)
        // retorna.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jobIdHolder[0] = cargaCsvUseCase.reprocesarMotor(idConciliacion);
            // Seguimos DENTRO de la transacción externa -- todavía no hay commit. Sin
            // importar qué tan rápido sea el hilo async o el thread pool, el listener
            // AFTER_COMMIT no puede haber disparado el motor todavía: es una garantía de
            // Spring, no una cuestión de timing.
            assertThat(motorArrancoMientrasTransaccionActiva)
                    .as("el motor NO debe arrancar mientras la transacción de reprocesarMotor sigue activa")
                    .isFalse();
        });
        transaccionExternaSigueActiva.set(false); // el bloque terminó -> ya hubo commit real

        JobStatus resultado = esperarJobTerminado(jobIdHolder[0]);

        assertThat(resultado.estado())
                .as("mensaje de error: %s", resultado.mensajeError())
                .isEqualTo("COMPLETED");
        assertThat(vecesQueArranco.get())
                .as("el motor debió arrancar exactamente una vez, después del commit")
                .isEqualTo(1);
        assertThat(motorArrancoMientrasTransaccionActiva)
                .as("verificación final: en ningún momento el motor vio la transacción todavía activa")
                .isFalse();

        // Y, ya que corrió, vio de verdad el bancario recién guardado -- no una foto vacía
        // (nTotal=0, el síntoma original del bug real: conciliación #29 con 22 bancarios
        // pendientes sin ninguna partida porque el motor había leído 0 y 0).
        assertThat(partidaJpaRepo.findByIdConciliacion(idConciliacion))
                .as("el bancario sin par debió recibir su partida PENDIENTE -- prueba de que el motor SÍ vio el dato committeado")
                .hasSize(1);
    }
}
