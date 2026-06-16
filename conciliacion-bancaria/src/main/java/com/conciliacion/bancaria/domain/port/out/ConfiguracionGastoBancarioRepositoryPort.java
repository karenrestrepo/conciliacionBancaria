package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.ConfiguracionGastoBancario;

import java.util.List;
import java.util.Optional;

public interface ConfiguracionGastoBancarioRepositoryPort {

    ConfiguracionGastoBancario guardar(ConfiguracionGastoBancario config);

    Optional<ConfiguracionGastoBancario> buscarPorId(Long id);

    List<ConfiguracionGastoBancario> listarPorCuenta(Long idCuenta);

    /** Devuelve solo los textos de descripción activos, usado por el motor de agrupación. */
    List<String> listarDescripcionesActivas(Long idCuenta);

    void eliminar(Long id);
}
