package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.PartidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartidaJpaRepository extends JpaRepository<PartidaEntity, Long> {

    List<PartidaEntity> findByIdConciliacion(Long idConciliacion);

    // Partidas pendientes sin justificar — usadas para validar el cierre
    @Query("""
            SELECT p FROM PartidaEntity p
            WHERE p.idConciliacion = :idConciliacion
              AND p.estado = 'PENDIENTE'
              AND (p.justificacion IS NULL OR p.justificacion = '')
            """)
    List<PartidaEntity> findPendientesSinJustificar(@Param("idConciliacion") Long idConciliacion);
}