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
}