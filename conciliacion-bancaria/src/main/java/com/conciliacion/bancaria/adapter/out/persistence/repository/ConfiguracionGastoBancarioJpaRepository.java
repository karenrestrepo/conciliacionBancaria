package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConfiguracionGastoBancarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConfiguracionGastoBancarioJpaRepository
        extends JpaRepository<ConfiguracionGastoBancarioEntity, Long> {

    List<ConfiguracionGastoBancarioEntity> findByIdCuenta(Long idCuenta);

    List<ConfiguracionGastoBancarioEntity> findByIdCuentaAndActivoTrue(Long idCuenta);

    @Query("SELECT c.descripcion FROM ConfiguracionGastoBancarioEntity c WHERE c.idCuenta = :idCuenta AND c.activo = TRUE")
    List<String> findDescripcionesActivasByIdCuenta(@Param("idCuenta") Long idCuenta);
}
