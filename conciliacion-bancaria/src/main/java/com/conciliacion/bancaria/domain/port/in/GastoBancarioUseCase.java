package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.ConfiguracionGastoBancario;

import java.util.List;

public interface GastoBancarioUseCase {

    ConfiguracionGastoBancario agregar(Long idCuenta, String descripcion);

    List<ConfiguracionGastoBancario> listar(Long idCuenta);

    void eliminar(Long idCuenta, Long id);
}
