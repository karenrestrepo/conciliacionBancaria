package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Banco;

import java.util.List;
import java.util.Optional;

public interface BancoRepositoryPort {

    Banco guardar(Banco banco);

    Optional<Banco> buscarPorId(Long id);

    List<Banco> listarActivos();

    List<Banco> listarTodos();

    boolean existePorNombre(String nombre);
}
