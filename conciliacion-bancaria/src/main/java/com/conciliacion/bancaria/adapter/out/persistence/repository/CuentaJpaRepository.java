package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.CuentaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CuentaJpaRepository extends JpaRepository<CuentaEntity, Long> {

    List<CuentaEntity> findByIdBanco(Long idBanco);

    List<CuentaEntity> findByActivoTrue();

    List<CuentaEntity> findByIdBancoAndActivoTrue(Long idBanco);

    boolean existsByIdBancoAndNumeroCuenta(Long idBanco, String numeroCuenta);

    @Query("""
        SELECT c FROM CuentaEntity c
        JOIN BancoEntity b ON b.id = c.idBanco
        WHERE c.activo = true AND b.empresaId = :empresaId
        """)
    List<CuentaEntity> findActivasByEmpresaId(@Param("empresaId") Long empresaId);
}
