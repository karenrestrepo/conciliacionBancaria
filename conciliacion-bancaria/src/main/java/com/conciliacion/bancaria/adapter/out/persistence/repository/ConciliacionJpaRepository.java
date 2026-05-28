package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConciliacionEntity;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConciliacionJpaRepository extends JpaRepository<ConciliacionEntity, Long> {

    Optional<ConciliacionEntity> findByPeriodo(String periodo);

    List<ConciliacionEntity> findByIdUsuarioCreador(Long idUsuario);

    boolean existsByPeriodoAndIdCuenta(String periodo, Long idCuenta);

    long countByEstado(EstadoConciliacion estado);

    @Query("SELECT c.estado FROM ConciliacionEntity c WHERE c.id = :id")
    Optional<EstadoConciliacion> findEstadoById(@Param("id") Long id);
}