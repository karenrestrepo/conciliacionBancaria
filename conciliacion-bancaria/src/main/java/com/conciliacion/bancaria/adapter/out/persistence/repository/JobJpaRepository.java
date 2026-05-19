package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConciliacionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobJpaRepository extends JpaRepository<ConciliacionJobEntity, String> {

    List<ConciliacionJobEntity> findByIdConciliacion(Long idConciliacion);
}