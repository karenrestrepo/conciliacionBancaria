package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Cuenta;

import java.util.List;
import java.util.Optional;

public interface CuentaRepositoryPort {

    Cuenta guardar(Cuenta cuenta);

    Optional<Cuenta> buscarPorId(Long id);

    List<Cuenta> listarPorBanco(Long idBanco);

    List<Cuenta> listarActivasPorEmpresa(Long empresaId);

    boolean existePorBancoYNumero(Long idBanco, String numeroCuenta);

    void eliminar(Long id);
}
