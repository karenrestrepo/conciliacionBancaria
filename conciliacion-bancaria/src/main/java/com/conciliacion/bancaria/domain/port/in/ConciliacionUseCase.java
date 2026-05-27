package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.Conciliacion;

import java.util.List;

public interface ConciliacionUseCase {

    Conciliacion iniciar(String periodo, Long idUsuario);

    Conciliacion obtenerPorId(Long id);

    List<Conciliacion> listarPorUsuario(Long idUsuario);

    List<Conciliacion> listarTodas();

    Conciliacion pasarARevision(Long idConciliacion);
}