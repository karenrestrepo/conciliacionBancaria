package com.conciliacion.bancaria.integration;

import com.conciliacion.bancaria.adapter.out.persistence.repository.ConciliacionJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.JobJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoBancarioJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoContableJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.SugerenciaJpaRepository;
import com.conciliacion.bancaria.application.usecase.CargaCsvUseCaseImpl;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.port.in.JobStatusUseCase.JobStatus;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.JobRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Verifica el rollback transaccional de {@code persistirResultadoMotorIncremental}: si un
 * paso falla a mitad de camino, TODO lo que ya se había escrito en esa transacción (incluida
 * la sugerencia recién guardada) se revierte -- en vez de quedar en el estado a medias que
 * causó el incidente real (321 sugerencias persistidas, 328 partidas pendientes intactas).
 *
 * Usa {@code @MockitoBean} (reemplazo completo del bean, no un spy) porque intentar espiar
 * la implementación real de {@code PartidaRepositoryPort} requiere que Byte Buddy subclasee
 * esa clase concreta para envolverla -- justo lo que falla en este entorno (JDK 25, no
 * soportado por la versión de Byte Buddy que trae el proyecto; confirmado empíricamente al
 * intentarlo con {@code @MockitoSpyBean}, con el mismo error ya documentado en otras partes
 * del proyecto para el mock de clases concretas). Mockear la interfaz completa sí funciona
 * sin problema, igual que en los tests unitarios existentes con Mockito puro -- por eso este
 * test vive separado de {@link MotorAsyncTransaccionalIntegrationTest}: reemplazar
 * {@code PartidaRepositoryPort} aquí significa que ese test NO puede compartir la misma clase,
 * porque dejaría de ejercitar el DELETE {@code @Modifying} real que prueba el fix del crash.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integración — rollback transaccional cuando falla la persistencia del motor incremental")
class MotorAsyncRollbackIntegrationTest {

    @Autowired
    private CargaCsvUseCaseImpl cargaCsvUseCase;
    @Autowired
    private ConciliacionRepositoryPort conciliacionRepo;
    @Autowired
    private MovimientoRepositoryPort movimientoRepo;
    @Autowired
    private JobRepositoryPort jobRepo;
    @Autowired
    private SugerenciaJpaRepository sugerenciaJpaRepo;
    @Autowired
    private MovimientoBancarioJpaRepository movimientoBancarioJpaRepo;
    @Autowired
    private MovimientoContableJpaRepository movimientoContableJpaRepo;
    @Autowired
    private JobJpaRepository jobJpaRepo;
    @Autowired
    private ConciliacionJpaRepository conciliacionJpaRepo;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @MockitoBean
    private PartidaRepositoryPort partidaRepo;

    private Long idConciliacion;

    @BeforeEach
    void setUp() {
        String periodo = "2098-" + String.format("%02d", 1 + (int) (Math.random() * 9));
        idConciliacion = conciliacionRepo.guardar(Conciliacion.builder()
                .periodo(periodo)
                .idCuenta(1L)
                .estado(EstadoConciliacion.BORRADOR)
                .idUsuarioCreador(1L)
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        // deleteByIdConciliacion es una query derivada -- necesita una transacción activa
        // igual que un @Modifying. Ver el comentario en MotorAsyncTransaccionalIntegrationTest
        // para el porqué de TransactionTemplate en vez de @Transactional en el método.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    sugerenciaJpaRepo.deleteByIdConciliacion(idConciliacion);
                    movimientoBancarioJpaRepo.deleteAll(movimientoBancarioJpaRepo.findByIdConciliacion(idConciliacion));
                    movimientoContableJpaRepo.deleteAll(movimientoContableJpaRepo.findByIdConciliacion(idConciliacion));
                    jobJpaRepo.deleteAll(jobJpaRepo.findByIdConciliacion(idConciliacion));
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
            JobStatus status = jobRepo.obtener(jobId);
            if ("COMPLETED".equals(status.estado()) || "FAILED".equals(status.estado())) {
                return status;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Job " + jobId + " no terminó dentro del tiempo esperado");
    }

    @Test
    @DisplayName("si eliminarPendientePorMovimiento falla, la sugerencia ya guardada en la misma "
            + "transacción se revierte, y jobRepo.fallar sigue registrando el error igual")
    void fallaAMitadDePersistenciaRevierteTransaccionCompleta() throws Exception {
        List<Movimiento> bancarios = movimientoRepo.guardarBancarios(
                List.of(movimiento(new BigDecimal("50.00"), "bancario a revertir")), idConciliacion);
        List<Movimiento> contables = movimientoRepo.guardarContables(
                List.of(movimiento(new BigDecimal("50.00"), "contable a revertir")), idConciliacion);
        assertThat(bancarios).hasSize(1);
        assertThat(contables).hasSize(1);

        // Fuerza una falla justo después de que sugerenciaRepo.guardarTodas ya corrió dentro
        // de la misma transacción -- si el rollback funciona, esa sugerencia nunca debe
        // quedar visible una vez que el job termina.
        doThrow(new RuntimeException("fallo forzado para probar rollback"))
                .when(partidaRepo).eliminarPendientePorMovimiento(anyLong(), anyString());

        String jobId = jobRepo.crearJob(idConciliacion);
        cargaCsvUseCase.ejecutarMotorIncrementalAsync(jobId, idConciliacion, contables);

        JobStatus resultado = esperarJobTerminado(jobId);

        assertThat(resultado.estado()).isEqualTo("FAILED");
        assertThat(resultado.mensajeError()).contains("fallo forzado para probar rollback");

        // La sugerencia que sí se había guardado dentro de la misma transacción también se
        // revirtió -- nunca queda un estado a medias como el del incidente real (321
        // sugerencias persistidas con 328 partidas pendientes intactas).
        assertThat(sugerenciaJpaRepo.findByIdConciliacion(idConciliacion))
                .as("el rollback debió revertir también la sugerencia ya guardada")
                .isEmpty();
    }
}
