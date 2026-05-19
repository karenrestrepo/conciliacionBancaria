package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.MovimientoBancarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoBancarioJpaRepository
        extends JpaRepository<MovimientoBancarioEntity, Long> {

    List<MovimientoBancarioEntity> findByIdConciliacion(Long idConciliacion);
}