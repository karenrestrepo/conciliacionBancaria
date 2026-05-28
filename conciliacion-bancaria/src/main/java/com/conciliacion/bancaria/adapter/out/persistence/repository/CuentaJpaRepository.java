package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.CuentaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuentaJpaRepository extends JpaRepository<CuentaEntity, Long> {

    List<CuentaEntity> findByIdBanco(Long idBanco);

    List<CuentaEntity> findByActivoTrue();

    List<CuentaEntity> findByIdBancoAndActivoTrue(Long idBanco);

    boolean existsByIdBancoAndNumeroCuenta(Long idBanco, String numeroCuenta);
}
