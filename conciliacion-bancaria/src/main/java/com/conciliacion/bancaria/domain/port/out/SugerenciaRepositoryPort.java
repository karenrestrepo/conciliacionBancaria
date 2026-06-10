package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Sugerencia;

import java.util.List;
import java.util.Optional;

public interface SugerenciaRepositoryPort {

    List<Sugerencia> guardarTodas(List<Sugerencia> sugerencias);

    List<Sugerencia> buscarPorConciliacion(Long idConciliacion);

    Optional<Sugerencia> buscarPorId(Long id);

    Sugerencia actualizar(Sugerencia sugerencia);

    void eliminarPorConciliacion(Long idConciliacion);

    void eliminarPendientesPorConciliacion(Long idConciliacion);
}