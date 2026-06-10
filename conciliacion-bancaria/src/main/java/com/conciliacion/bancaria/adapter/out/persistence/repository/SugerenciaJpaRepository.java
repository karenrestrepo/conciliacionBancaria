package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.SugerenciaEntity;
import com.conciliacion.bancaria.shared.EstadoSugerencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SugerenciaJpaRepository extends JpaRepository<SugerenciaEntity, Long> {

    List<SugerenciaEntity> findByIdConciliacion(Long idConciliacion);

    List<SugerenciaEntity> findByIdConciliacionAndEstado(Long idConciliacion,
                                                         EstadoSugerencia estado);
    long countByEstado(EstadoSugerencia estado);

    void deleteByIdConciliacion(Long idConciliacion);

    @org.springframework.data.jpa.repository.Modifying
    void deleteByIdConciliacionAndEstado(Long idConciliacion, EstadoSugerencia estado);
}