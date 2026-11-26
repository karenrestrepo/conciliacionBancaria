package com.conciliacion.bancaria.integration;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConciliacionEntity;
import com.conciliacion.bancaria.adapter.out.persistence.entity.PartidaEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.ConciliacionJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.PartidaJpaRepository;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba {@code findPendientesDeOtrasConciliaciones} contra la BD de tests real (ver la
 * nota en {@link ConciliacionIntegrationTest} sobre Testcontainers vs BD persistente) --
 * es una query JPQL con subquery correlacionada, no vale la pena reproducirla con mocks.
 *
 * Escenario: cuenta 1 tiene tres conciliaciones. B es la actual. A tiene tres partidas:
 * una ARRASTRADA hacia el período de B (debe aparecer), una ARRASTRADA hacia OTRO período
 * (no debe aparecer) y una PENDIENTE suelta sin arrastrar (no debe aparecer -- decisión de
 * producto: una pendiente de otra conciliación ya es visible en la pantalla de Partidas de
 * SU PROPIA conciliación, no hace falta duplicarla acá "por si acaso", que es justo lo que
 * causaba la mezcla de períodos reportada).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Integración — PartidaJpaRepository.findPendientesDeOtrasConciliaciones respeta periodoArrastre")
class PartidaJpaRepositoryIntegrationTest {

    @Autowired
    private PartidaJpaRepository partidaJpaRepo;
    @Autowired
    private ConciliacionJpaRepository conciliacionJpaRepo;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private Long idConciliacionA;
    private Long idConciliacionB;
    private Long idConciliacionOtroPeriodo;

    @BeforeEach
    void setUp() {
        idConciliacionA = crearConciliacion("2094-01");
        idConciliacionB = crearConciliacion("2094-02");
        idConciliacionOtroPeriodo = crearConciliacion("2094-03");
    }

    @AfterEach
    void tearDown() {
        // deleteByIdConciliacion es una query derivada de Spring Data -- necesita una
        // transacción activa igual que un @Modifying, no la trae gratis (mismo hallazgo
        // que en MotorAsyncTransaccionalIntegrationTest).
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    partidaJpaRepo.deleteByIdConciliacion(idConciliacionA);
                    partidaJpaRepo.deleteByIdConciliacion(idConciliacionB);
                    partidaJpaRepo.deleteByIdConciliacion(idConciliacionOtroPeriodo);
                    conciliacionJpaRepo.deleteById(idConciliacionA);
                    conciliacionJpaRepo.deleteById(idConciliacionB);
                    conciliacionJpaRepo.deleteById(idConciliacionOtroPeriodo);
                });
    }

    private Long crearConciliacion(String periodo) {
        return conciliacionJpaRepo.save(ConciliacionEntity.builder()
                .periodo(periodo)
                .idCuenta(1L)
                .estado(EstadoConciliacion.BORRADOR)
                .idUsuarioCreador(1L)
                .tsCreacion(LocalDateTime.now())
                .build()).getId();
    }

    private PartidaEntity partida(Long idConciliacion, String estado, String periodoArrastre) {
        return partidaJpaRepo.save(PartidaEntity.builder()
                .idConciliacion(idConciliacion)
                .idMovimiento(1L)
                .tipoOrigen("BANCARIO")
                .estado(estado)
                .periodoArrastre(periodoArrastre)
                .build());
    }

    @Test
    @DisplayName("sólo trae ARRASTRADA cuyo periodoArrastre coincide con el período de la conciliación actual")
    void soloTraeArrastradasAlPeriodoActual() {
        PartidaEntity arrastradaAlPeriodoB = partida(idConciliacionA, "ARRASTRADA", "2094-02");
        partida(idConciliacionA, "ARRASTRADA", "2094-03");   // arrastrada a OTRO período -- no debe aparecer
        partida(idConciliacionA, "PENDIENTE", null);          // pendiente suelta, nunca arrastrada -- no debe aparecer

        List<PartidaEntity> resultado =
                partidaJpaRepo.findPendientesDeOtrasConciliaciones(1L, idConciliacionB);

        assertThat(resultado)
                .extracting(PartidaEntity::getId)
                .containsExactly(arrastradaAlPeriodoB.getId());
    }

    @Test
    @DisplayName("una conciliación sin nada arrastrado hacia ella no trae nada")
    void sinArrastresNoTraeNada() {
        partida(idConciliacionA, "ARRASTRADA", "2094-03");
        partida(idConciliacionA, "PENDIENTE", null);

        List<PartidaEntity> resultado =
                partidaJpaRepo.findPendientesDeOtrasConciliaciones(1L, idConciliacionB);

        assertThat(resultado).isEmpty();
    }
}
