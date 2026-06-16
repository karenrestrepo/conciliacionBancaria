package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.MovimientoBancarioEntity;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MovimientoBancarioJpaRepository
        extends JpaRepository<MovimientoBancarioEntity, Long> {

    List<MovimientoBancarioEntity> findByIdConciliacion(Long idConciliacion);

    @Modifying
    @Query("UPDATE MovimientoBancarioEntity m SET m.estadoConciliacion = :estado WHERE m.idConciliacion = :idConciliacion")
    void resetEstados(@Param("idConciliacion") Long idConciliacion,
                      @Param("estado") EstadoMovimiento estado);

    @Modifying
    @Query("UPDATE MovimientoBancarioEntity m SET m.estadoConciliacion = 'PENDIENTE' WHERE m.idConciliacion = :idConciliacion AND m.estadoConciliacion = 'SUGERIDO'")
    void resetSugeridos(@Param("idConciliacion") Long idConciliacion);

    @Query("SELECT m FROM MovimientoBancarioEntity m WHERE m.idConciliacion = :idConciliacion AND m.estadoConciliacion = 'PENDIENTE'")
    List<MovimientoBancarioEntity> findPendientesByIdConciliacion(@Param("idConciliacion") Long idConciliacion);

    List<MovimientoBancarioEntity> findByIdIn(List<Long> ids);

    @Query("SELECT m FROM MovimientoBancarioEntity m WHERE m.idConciliacion = :idConciliacion AND m.estadoConciliacion = 'AGRUPADO' ORDER BY m.descripcion, m.fecha")
    List<MovimientoBancarioEntity> findAgrupadosByIdConciliacion(@Param("idConciliacion") Long idConciliacion);

    @Modifying
    @Query("DELETE FROM MovimientoBancarioEntity m WHERE m.idConciliacion = :idConciliacion AND m.estadoConciliacion != 'CONCILIADO'")
    void deleteNoConciliadosByIdConciliacion(@Param("idConciliacion") Long idConciliacion);

    @Modifying
    @Query("UPDATE MovimientoBancarioEntity m SET m.estadoConciliacion = :estado WHERE m.id = :id")
    void actualizarEstado(@Param("id") Long id, @Param("estado") EstadoMovimiento estado);
}