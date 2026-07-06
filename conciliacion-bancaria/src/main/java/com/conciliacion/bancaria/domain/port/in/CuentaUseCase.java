package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.Cuenta;
import com.conciliacion.bancaria.shared.TipoCuenta;

import java.util.List;

public interface CuentaUseCase {

    Cuenta crear(Long idBanco, String numeroCuenta, TipoCuenta tipo, String descripcion,
                 boolean auxiliarConjunto);

    Cuenta obtenerPorId(Long id);

    List<Cuenta> listarPorBanco(Long idBanco);

    List<Cuenta> listarActivasPorEmpresa(Long empresaId);

    Cuenta cambiarEstado(Long id, boolean activo);

    void eliminar(Long id);
}
