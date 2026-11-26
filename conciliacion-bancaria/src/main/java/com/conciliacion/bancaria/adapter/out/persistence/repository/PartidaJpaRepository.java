package com.conciliacion.bancaria.adapter.out.persistence.repository;

import com.conciliacion.bancaria.adapter.out.persistence.entity.PartidaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface PartidaJpaRepository extends JpaRepository<PartidaEntity, Long> {

    List<PartidaEntity> findByIdConciliacion(Long idConciliacion);

    // Partidas pendientes sin justificar — usadas para validar el cierre
    @Query("""
            SELECT p FROM PartidaEntity p
            WHERE p.idConciliacion = :idConciliacion
              AND p.estado = 'PENDIENTE'
              AND (p.justificacion IS NULL OR p.justificacion = '')
            """)
    List<PartidaEntity> findPendientesSinJustificar(@Param("idConciliacion") Long idConciliacion);

    void deleteByIdConciliacion(Long idConciliacion);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PartidaEntity p WHERE p.idConciliacion = :idConciliacion AND p.estado = 'PENDIENTE'")
    void deletePendientesByIdConciliacion(@Param("idConciliacion") Long idConciliacion);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PartidaEntity p WHERE p.idMovimiento = :idMovimiento AND p.tipoOrigen = :tipoOrigen AND p.estado = 'PENDIENTE'")
    void deletePendienteByMovimiento(@Param("idMovimiento") Long idMovimiento,
                                     @Param("tipoOrigen") String tipoOrigen);

    // Borra TODAS las partidas de un movimiento (cualquier estado) -- usado al dar de baja el
    // movimiento en la recarga del auxiliar: si el movimiento se elimina, todas sus partidas
    // quedarían huérfanas.
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PartidaEntity p WHERE p.idMovimiento = :idMovimiento AND p.tipoOrigen = :tipoOrigen")
    void deleteByMovimiento(@Param("idMovimiento") Long idMovimiento,
                            @Param("tipoOrigen") String tipoOrigen);

    List<PartidaEntity> findByIdMovimientoAndTipoOrigen(Long idMovimiento, String tipoOrigen);

    List<PartidaEntity> findByGrupoCruce(String grupoCruce);

    // Sólo partidas ARRASTRADA cuyo periodoArrastre coincide EXACTAMENTE con el período de
    // la conciliación actual -- es decir, partidas que alguien mandó explícitamente a este
    // período con el botón "Próximo mes", no cualquier pendiente suelta de otra
    // conciliación de la misma cuenta. Antes traía cualquier PENDIENTE/ARRASTRADA sin
    // comparar períodos, así que las pendientes de julio se colaban en la vista de junio
    // si julio se conciliaba primero. Decisión de producto (documentada, no asumida):
    // ya no se muestran "pendientes sueltas" de otro período que nadie arrastró -- esas
    // ya son visibles en la pantalla de Partidas de SU PROPIA conciliación; duplicarlas
    // aquí "por si acaso" es exactamente lo que causaba la mezcla.
    @Query("""
            SELECT p FROM PartidaEntity p
            JOIN ConciliacionEntity c ON c.id = p.idConciliacion
            WHERE c.idCuenta = :idCuenta
              AND c.id <> :idConciliacionActual
              AND p.estado = 'ARRASTRADA'
              AND p.periodoArrastre = (
                  SELECT ca.periodo FROM ConciliacionEntity ca WHERE ca.id = :idConciliacionActual
              )
            """)
    List<PartidaEntity> findPendientesDeOtrasConciliaciones(
            @Param("idCuenta") Long idCuenta,
            @Param("idConciliacionActual") Long idConciliacionActual);

    @Query("""
            SELECT p.idMovimiento FROM PartidaEntity p
            WHERE p.idConciliacion = :idConciliacion
              AND p.tipoOrigen = :tipoOrigen
              AND p.estado = 'PENDIENTE'
            """)
    Set<Long> findIdsMovimientoConPendiente(@Param("idConciliacion") Long idConciliacion,
                                            @Param("tipoOrigen") String tipoOrigen);
}