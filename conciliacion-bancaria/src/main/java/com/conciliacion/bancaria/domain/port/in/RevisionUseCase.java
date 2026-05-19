package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.Sugerencia;

import java.util.List;

public interface RevisionUseCase {

    List<Sugerencia> obtenerSugerencias(Long idConciliacion);

    Sugerencia aceptarSugerencia(Long idSugerencia, Long idUsuario);

    Sugerencia rechazarSugerencia(Long idSugerencia, Long idUsuario);
}