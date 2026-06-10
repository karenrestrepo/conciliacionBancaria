package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.CuentaEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.BancoJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.CuentaJpaRepository;
import com.conciliacion.bancaria.domain.model.Cuenta;
import com.conciliacion.bancaria.domain.port.out.CuentaRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CuentaRepositoryAdapter implements CuentaRepositoryPort {

    private final CuentaJpaRepository cuentaJpaRepository;
    private final BancoJpaRepository bancoJpaRepository;

    @Override
    public Cuenta guardar(Cuenta cuenta) {
        return toDomain(cuentaJpaRepository.save(toEntity(cuenta)));
    }

    @Override
    public Optional<Cuenta> buscarPorId(Long id) {
        return cuentaJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Cuenta> listarPorBanco(Long idBanco) {
        return cuentaJpaRepository.findByIdBanco(idBanco).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Cuenta> listarActivas() {
        return cuentaJpaRepository.findByActivoTrue().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existePorBancoYNumero(Long idBanco, String numeroCuenta) {
        return cuentaJpaRepository.existsByIdBancoAndNumeroCuenta(idBanco, numeroCuenta);
    }

    @Override
    public void eliminar(Long id) {
        cuentaJpaRepository.deleteById(id);
    }

    private Cuenta toDomain(CuentaEntity e) {
        String nombreBanco = bancoJpaRepository.findById(e.getIdBanco())
                .map(b -> b.getNombre())
                .orElse(null);
        return Cuenta.builder()
                .id(e.getId())
                .idBanco(e.getIdBanco())
                .nombreBanco(nombreBanco)
                .numeroCuenta(e.getNumeroCuenta())
                .tipo(e.getTipo())
                .descripcion(e.getDescripcion())
                .activo(e.getActivo())
                .tsCreacion(e.getTsCreacion())
                .build();
    }

    private CuentaEntity toEntity(Cuenta c) {
        return CuentaEntity.builder()
                .id(c.getId())
                .idBanco(c.getIdBanco())
                .numeroCuenta(c.getNumeroCuenta())
                .tipo(c.getTipo())
                .descripcion(c.getDescripcion())
                .activo(c.getActivo() != null ? c.getActivo() : true)
                .tsCreacion(c.getTsCreacion())
                .build();
    }
}
