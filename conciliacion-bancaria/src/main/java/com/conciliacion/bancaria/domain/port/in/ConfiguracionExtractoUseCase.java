package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;

import java.util.List;

public interface ConfiguracionExtractoUseCase {

    ConfiguracionExtracto crear(ConfiguracionExtracto c);

    ConfiguracionExtracto actualizar(Long id, ConfiguracionExtracto c);

    ConfiguracionExtracto obtenerPorId(Long id);

    List<ConfiguracionExtracto> listarPorBanco(Long idBanco);

    void eliminar(Long id);
}
