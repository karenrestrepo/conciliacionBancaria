package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PartidaRepositoryPort {

    PartidaConciliatoria guardar(PartidaConciliatoria partida);

    List<PartidaConciliatoria> guardarTodas(List<PartidaConciliatoria> partidas);

    List<PartidaConciliatoria> buscarPorConciliacion(Long idConciliacion);

    List<PartidaConciliatoria> buscarPendientesSinJustificar(Long idConciliacion);

    Optional<PartidaConciliatoria> buscarPorId(Long id);

    PartidaConciliatoria actualizar(PartidaConciliatoria partida);

    void eliminarPorConciliacion(Long idConciliacion);

    void eliminarPendientesPorConciliacion(Long idConciliacion);

    void eliminarPendientePorMovimiento(Long idMovimiento, String tipoOrigen);

    /** Borra todas las partidas de un movimiento (cualquier estado); usado al eliminar el movimiento. */
    void eliminarPartidasPorMovimiento(Long idMovimiento, String tipoOrigen);

    /** Partida(s) asociadas a un movimiento (para leer su grupo de cruce, estado, etc.). */
    List<PartidaConciliatoria> buscarPorMovimiento(Long idMovimiento, String tipoOrigen);

    /** Todas las partidas que comparten un mismo grupo de cruce manual. */
    List<PartidaConciliatoria> buscarPorGrupoCruce(String grupoCruce);

    List<PartidaConciliatoria> buscarPendientesDeOtrasConciliaciones(Long idCuenta, Long idConciliacionActual);

    /**
     * Ids de movimiento que YA tienen una partida PENDIENTE para esta conciliación y este
     * tipoOrigen -- usado para filtrar duplicados antes de insertar partidas nuevas cuando
     * el motor completo reprocesa movimientos ya vistos en una corrida anterior (ej. cada
     * extracto de tarjeta de crédito subido en una conciliación con auxiliar_conjunto).
     */
    Set<Long> buscarIdsMovimientoConPendiente(Long idConciliacion, String tipoOrigen);
}