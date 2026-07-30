package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Sugerencia;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SugerenciaRepositoryPort {

    List<Sugerencia> guardarTodas(List<Sugerencia> sugerencias);

    List<Sugerencia> buscarPorConciliacion(Long idConciliacion);

    Optional<Sugerencia> buscarPorId(Long id);

    Sugerencia actualizar(Sugerencia sugerencia);

    void eliminarPorConciliacion(Long idConciliacion);

    void eliminarPendientesPorConciliacion(Long idConciliacion);

    Set<Long> buscarBancarioIdsConSugerenciaPendiente(Long idConciliacion);

    /**
     * Sugerencia activa (PENDIENTE_REVISION o ACEPTADA) para un movimiento contable dado.
     * Ignora sugerencias RECHAZADA históricas — esas no bloquean borrar el contable.
     * Usado para decidir si un contable "desaparecido" en una recarga del auxiliar
     * necesita revertir su emparejamiento antes de eliminarse (evita violar la FK de
     * sugerencias_conciliacion, que no tiene ON DELETE CASCADE).
     */
    Optional<Sugerencia> buscarActivaPorMovimientoContable(Long idMovContable);

    void eliminarPorId(Long id);
}