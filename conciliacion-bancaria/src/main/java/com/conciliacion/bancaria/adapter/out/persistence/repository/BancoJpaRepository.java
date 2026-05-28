package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.BancoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BancoJpaRepository extends JpaRepository<BancoEntity, Long> {

    List<BancoEntity> findByActivoTrue();

    boolean existsByNombre(String nombre);
}
