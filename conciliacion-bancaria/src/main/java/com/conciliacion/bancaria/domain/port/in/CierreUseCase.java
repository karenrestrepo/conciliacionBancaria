package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;

public interface CierreUseCase {

    // Valida partidas y cierra — lanza PartidaPendienteException si hay sin justificar
    Conciliacion cerrar(Long idConciliacion, Long idUsuarioContador);

    PartidaConciliatoria justificarPartida(Long idPartida, String justificacion,
                                           java.time.LocalDate fecha);

    PartidaConciliatoria arrastrarPartida(Long idPartida, String periodoDestino, Long idUsuario);

    java.util.List<PartidaConciliatoria> listarPartidas(Long idConciliacion);

    java.util.List<PartidaConciliatoria> listarPartidasHistoricas(Long idConciliacion);
}