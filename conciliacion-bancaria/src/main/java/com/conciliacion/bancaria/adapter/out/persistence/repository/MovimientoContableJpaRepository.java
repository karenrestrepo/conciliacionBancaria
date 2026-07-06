package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.MovimientoContableEntity;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimientoContableJpaRepository
        extends JpaRepository<MovimientoContableEntity, Long> {

    List<MovimientoContableEntity> findByIdConciliacion(Long idConciliacion);

    @Modifying
    @Query("UPDATE MovimientoContableEntity m SET m.estadoConciliacion = :estado WHERE m.idConciliacion = :idConciliacion")
    void resetEstados(@Param("idConciliacion") Long idConciliacion,
                      @Param("estado") EstadoMovimiento estado);

    @Modifying
    @Query("UPDATE MovimientoContableEntity m SET m.estadoConciliacion = 'PENDIENTE' WHERE m.idConciliacion = :idConciliacion AND m.estadoConciliacion = 'SUGERIDO'")
    void resetSugeridos(@Param("idConciliacion") Long idConciliacion);

    @Modifying
    @Query("DELETE FROM MovimientoContableEntity m WHERE m.idConciliacion = :idConciliacion AND m.estadoConciliacion != 'CONCILIADO'")
    void deleteNoConciliadosByIdConciliacion(@Param("idConciliacion") Long idConciliacion);

    @Modifying
    @Query("UPDATE MovimientoContableEntity m SET m.estadoConciliacion = :estado WHERE m.id = :id")
    void actualizarEstado(@Param("id") Long id, @Param("estado") EstadoMovimiento estado);

    @Query("SELECT COUNT(m) FROM MovimientoContableEntity m JOIN ConciliacionEntity c ON c.id = m.idConciliacion WHERE c.empresaId = :empresaId")
    long countByEmpresaId(@Param("empresaId") Long empresaId);
}
