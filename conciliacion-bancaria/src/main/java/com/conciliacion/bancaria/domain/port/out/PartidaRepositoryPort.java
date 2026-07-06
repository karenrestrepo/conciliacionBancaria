package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;

import java.util.List;
import java.util.Optional;

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

    List<PartidaConciliatoria> buscarPendientesDeOtrasConciliaciones(Long idCuenta, Long idConciliacionActual);
}