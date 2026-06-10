package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.model.Cuenta;
import com.conciliacion.bancaria.domain.port.in.BancoUseCase;
import com.conciliacion.bancaria.domain.port.in.CuentaUseCase;
import com.conciliacion.bancaria.domain.port.out.CuentaRepositoryPort;
import com.conciliacion.bancaria.shared.TipoCuenta;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CuentaUseCaseImpl implements CuentaUseCase {

    private final CuentaRepositoryPort cuentaRepo;
    private final BancoUseCase bancoUseCase;

    @Override
    @Transactional
    public Cuenta crear(Long idBanco, String numeroCuenta, TipoCuenta tipo, String descripcion) {
        // Verifica que el banco exista (lanza excepción si no)
        bancoUseCase.obtenerPorId(idBanco);

        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            throw new IllegalArgumentException("El número de cuenta no puede estar vacío");
        }
        if (cuentaRepo.existePorBancoYNumero(idBanco, numeroCuenta.trim())) {
            throw new IllegalStateException(
                    "Ya existe una cuenta con el número " + numeroCuenta + " para ese banco");
        }

        Cuenta nueva = Cuenta.builder()
                .idBanco(idBanco)
                .numeroCuenta(numeroCuenta.trim())
                .tipo(tipo != null ? tipo : TipoCuenta.CORRIENTE)
                .descripcion(descripcion)
                .activo(true)
                .build();

        return cuentaRepo.guardar(nueva);
    }

    @Override
    @Transactional(readOnly = true)
    public Cuenta obtenerPorId(Long id) {
        return cuentaRepo.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cuenta no encontrada: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cuenta> listarPorBanco(Long idBanco) {
        return cuentaRepo.listarPorBanco(idBanco);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cuenta> listarActivas() {
        return cuentaRepo.listarActivas();
    }

    @Override
    @Transactional
    public Cuenta cambiarEstado(Long id, boolean activo) {
        Cuenta cuenta = obtenerPorId(id);
        Cuenta actualizada = Cuenta.builder()
                .id(cuenta.getId())
                .idBanco(cuenta.getIdBanco())
                .numeroCuenta(cuenta.getNumeroCuenta())
                .tipo(cuenta.getTipo())
                .descripcion(cuenta.getDescripcion())
                .activo(activo)
                .tsCreacion(cuenta.getTsCreacion())
                .build();
        return cuentaRepo.guardar(actualizada);
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        obtenerPorId(id);
        cuentaRepo.eliminar(id);
    }
}
