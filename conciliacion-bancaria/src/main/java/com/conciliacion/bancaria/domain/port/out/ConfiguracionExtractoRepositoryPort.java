package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;

import java.util.List;
import java.util.Optional;

public interface ConfiguracionExtractoRepositoryPort {

    ConfiguracionExtracto guardar(ConfiguracionExtracto c);

    Optional<ConfiguracionExtracto> buscarPorId(Long id);

    List<ConfiguracionExtracto> listarPorBanco(Long idBanco);

    void eliminar(Long id);
}
