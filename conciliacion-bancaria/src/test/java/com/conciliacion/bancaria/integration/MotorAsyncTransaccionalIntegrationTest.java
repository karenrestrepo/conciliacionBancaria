package com.conciliacion.bancaria.integration;

import com.conciliacion.bancaria.adapter.out.persistence.repository.ConciliacionJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.JobJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoBancarioJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoContableJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.PartidaJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.SugerenciaJpaRepository;
import com.conciliacion.bancaria.application.usecase.CargaCsvUseCaseImpl;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduce, contra la BD de tests real (no mocks -- ver la nota en
 * {@link ConciliacionIntegrationTest} sobre por qué este proyecto usa una base de datos de
 * pruebas persistente en el mismo contenedor en vez de Testcontainers), el bug real de
 * producción: {@code ejecutarMotorAsync}/{@code ejecutarMotorIncrementalAsync} son
 * {@code @Async} y por lo tanto NO heredan ninguna transacción del método que las dispara.
 * Antes del fix, cualquier consulta {@code @Modifying} ejecutada ahí dentro (por ejemplo
 * {@code eliminarPendientePorMovimiento}) fallaba con {@code TransactionRequiredException} --
 * un bug invisible para los tests unitarios con Mockito porque esos jamás ejecutan una
 * consulta JPA real ni dependen de una transacción real.
 *
 * El escenario de rollback (una falla a mitad de la persistencia revierte también lo que
 * ya se había escrito) se prueba aparte, en {@link MotorAsyncRollbackIntegrationTest} --
 * requiere reemplazar por completo {@code PartidaRepositoryPort} con un mock, y sumarlo acá
 * habría hecho que este test dejara de ejercitar el DELETE @Modifying real contra la BD,
 * que es justo lo que este test necesita probar.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integración — @Transactional en los motores @Async")
class MotorAsyncTransaccionalIntegrationTest {

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
    private PartidaJpaRepository partidaJpaRepo;
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

    private Long idConciliacion;

    @BeforeEach
    void setUp() {
        // Período con margen de sobra para no chocar con datos reales de otras pruebas.
        String periodo = "2097-" + String.format("%02d", 1 + (int) (Math.random() * 9));
        idConciliacion = conciliacionRepo.guardar(Conciliacion.builder()
                .periodo(periodo)
                .idCuenta(1L)
                .estado(EstadoConciliacion.BORRADOR)
                .idUsuarioCreador(1L)
                .build()).getId();
    }

    @AfterEach
    void tearDown() {
        // deleteByIdConciliacion es una query derivada (no un método CRUD base de
        // SimpleJpaRepository) -- necesita una transacción activa igual que cualquier
        // @Modifying, algo que NO trae gratis por default. Se descubrió precisamente al
        // correr este test contra la BD real: el primer intento de este tearDown sin
        // envolver nada falló con el mismo TransactionRequiredException que motivó todo
        // este fix. Usar TransactionTemplate en vez de @Transactional en el método porque
        // Spring Test solo reconoce @Transactional declarativo en el método @Test (o la
        // clase), no en @AfterEach de forma independiente.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    partidaJpaRepo.deleteByIdConciliacion(idConciliacion);
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

    /** Job asíncrono real, en el pool conciliacionExecutor -- hay que esperar a que termine. */
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
    @DisplayName("ejecutarMotorIncrementalAsync contra DB real: no lanza TransactionRequiredException, "
            + "genera la sugerencia y limpia la partida pendiente del bancario emparejado")
    void motorIncrementalNoLanzaTransactionRequiredException() throws Exception {
        // Estado previo a la carga del auxiliar: un bancario PENDIENTE con su partida
        // pendiente ya creada -- exactamente lo que deja ejecutarMotorAsync en producción
        // cuando corre antes de que exista algún contable con qué cruzar.
        List<Movimiento> bancarios = movimientoRepo.guardarBancarios(
                List.of(movimiento(new BigDecimal("100.00"), "bancario real")), idConciliacion);
        Long idBancario = bancarios.get(0).getId();

        partidaJpaRepo.save(toEntity(PartidaConciliatoria.builder()
                .idConciliacion(idConciliacion)
                .idMovimiento(idBancario)
                .tipoOrigen("BANCARIO")
                .estado("PENDIENTE")
                .build()));

        List<Movimiento> contables = movimientoRepo.guardarContables(
                List.of(movimiento(new BigDecimal("100.00"), "contable real")), idConciliacion);

        String jobId = jobRepo.crearJob(idConciliacion);
        cargaCsvUseCase.ejecutarMotorIncrementalAsync(jobId, idConciliacion, contables);

        JobStatus resultado = esperarJobTerminado(jobId);

        assertThat(resultado.estado())
                .as("mensaje de error: %s", resultado.mensajeError())
                .isEqualTo("COMPLETED");
        assertThat(resultado.mensajeError()).isNull();

        assertThat(sugerenciaJpaRepo.findByIdConciliacion(idConciliacion)).hasSize(1);
        assertThat(partidaJpaRepo.findByIdConciliacion(idConciliacion))
                .as("la partida pendiente del bancario emparejado debió quedar limpiada")
                .isEmpty();
    }

    private com.conciliacion.bancaria.adapter.out.persistence.entity.PartidaEntity toEntity(
            PartidaConciliatoria p) {
        return com.conciliacion.bancaria.adapter.out.persistence.entity.PartidaEntity.builder()
                .idConciliacion(p.getIdConciliacion())
                .idMovimiento(p.getIdMovimiento())
                .tipoOrigen(p.getTipoOrigen())
                .estado(p.getEstado())
                .build();
    }
}
