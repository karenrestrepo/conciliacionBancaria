package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.Cuenta;
import com.conciliacion.bancaria.shared.TipoCuenta;

import java.util.List;

public interface CuentaUseCase {

    Cuenta crear(Long idBanco, String numeroCuenta, TipoCuenta tipo, String descripcion);

    Cuenta obtenerPorId(Long id);

    List<Cuenta> listarPorBanco(Long idBanco);

    List<Cuenta> listarActivas();
}
