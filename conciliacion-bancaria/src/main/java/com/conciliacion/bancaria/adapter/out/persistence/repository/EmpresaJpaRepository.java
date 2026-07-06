package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.EmpresaEntity;
import com.conciliacion.bancaria.shared.TipoIdentificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmpresaJpaRepository extends JpaRepository<EmpresaEntity, Long> {

    boolean existsByTipoIdentificacionAndNumeroIdentificacion(
            TipoIdentificacion tipo, String numero);

    Optional<EmpresaEntity> findByTipoIdentificacionAndNumeroIdentificacion(
            TipoIdentificacion tipo, String numero);
}
