package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.Banco;

import java.util.List;

public interface BancoUseCase {

    Banco crear(String nombre, String codigo, Long empresaId);

    Banco obtenerPorId(Long id);

    List<Banco> listarActivos(Long empresaId);

    void desactivar(Long id);
}
