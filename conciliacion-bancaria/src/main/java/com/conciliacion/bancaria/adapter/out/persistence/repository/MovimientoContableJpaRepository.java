package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.MovimientoContableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoContableJpaRepository
        extends JpaRepository<MovimientoContableEntity, Long> {

    List<MovimientoContableEntity> findByIdConciliacion(Long idConciliacion);
}
