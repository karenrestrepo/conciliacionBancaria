package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.SugerenciaEntity;
import com.conciliacion.bancaria.shared.EstadoSugerencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SugerenciaJpaRepository extends JpaRepository<SugerenciaEntity, Long> {

    List<SugerenciaEntity> findByIdConciliacion(Long idConciliacion);

    List<SugerenciaEntity> findByIdConciliacionAndEstado(Long idConciliacion,
                                                         EstadoSugerencia estado);
    long countByEstado(EstadoSugerencia estado);

    void deleteByIdConciliacion(Long idConciliacion);

    @org.springframework.data.jpa.repository.Modifying
    void deleteByIdConciliacionAndEstado(Long idConciliacion, EstadoSugerencia estado);

    @Query("SELECT s.idMovBancario FROM SugerenciaEntity s WHERE s.idConciliacion = :idConciliacion AND s.estado = 'PENDIENTE_REVISION'")
    Set<Long> findBancarioIdsConSugerenciaPendiente(@Param("idConciliacion") Long idConciliacion);

    @Query("SELECT s.idMovBancario FROM SugerenciaEntity s WHERE s.idConciliacion = :idConciliacion AND s.estado IN ('PENDIENTE_REVISION', 'ACEPTADA')")
    Set<Long> findBancarioIdsConSugerenciaActiva(@Param("idConciliacion") Long idConciliacion);

    @Query("SELECT s.idMovContable FROM SugerenciaEntity s WHERE s.idConciliacion = :idConciliacion AND s.estado IN ('PENDIENTE_REVISION', 'ACEPTADA')")
    Set<Long> findContableIdsConSugerenciaActiva(@Param("idConciliacion") Long idConciliacion);

    @Query("SELECT s FROM SugerenciaEntity s WHERE s.idMovContable = :idMovContable "
            + "AND s.estado IN ('PENDIENTE_REVISION', 'ACEPTADA')")
    Optional<SugerenciaEntity> findActivaByIdMovContable(@Param("idMovContable") Long idMovContable);

    @Query("SELECT COUNT(s) FROM SugerenciaEntity s JOIN ConciliacionEntity c ON c.id = s.idConciliacion WHERE c.empresaId = :empresaId")
    long countByEmpresaId(@Param("empresaId") Long empresaId);

    @Query("SELECT COUNT(s) FROM SugerenciaEntity s JOIN ConciliacionEntity c ON c.id = s.idConciliacion WHERE c.empresaId = :empresaId AND s.estado = :estado")
    long countByEmpresaIdAndEstado(@Param("empresaId") Long empresaId, @Param("estado") EstadoSugerencia estado);
}