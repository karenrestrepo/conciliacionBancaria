package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConfiguracionExtractoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfiguracionExtractoJpaRepository extends JpaRepository<ConfiguracionExtractoEntity, Long> {

    List<ConfiguracionExtractoEntity> findByIdBanco(Long idBanco);

    List<ConfiguracionExtractoEntity> findByIdBancoAndActivoTrue(Long idBanco);
}
