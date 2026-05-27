package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import java.util.List;
import java.util.Optional;

public interface ConciliacionRepositoryPort {

    Conciliacion guardar(Conciliacion conciliacion);

    Optional<Conciliacion> buscarPorId(Long id);

    Optional<Conciliacion> buscarPorPeriodo(String periodo);

    List<Conciliacion> buscarPorUsuarioCreador(Long idUsuario);

    List<Conciliacion> buscarTodas();

    boolean existePorPeriodo(String periodo);

    EstadoConciliacion obtenerEstado(Long id);
}