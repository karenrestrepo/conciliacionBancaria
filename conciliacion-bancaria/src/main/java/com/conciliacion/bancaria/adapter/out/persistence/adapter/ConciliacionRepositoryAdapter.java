package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConciliacionEntity;
import com.conciliacion.bancaria.adapter.out.persistence.entity.CuentaEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.BancoJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.ConciliacionJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.CuentaJpaRepository;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConciliacionRepositoryAdapter implements ConciliacionRepositoryPort {

    private final ConciliacionJpaRepository jpaRepository;
    private final CuentaJpaRepository cuentaJpaRepository;
    private final BancoJpaRepository bancoJpaRepository;

    @Override
    public Conciliacion guardar(Conciliacion conciliacion) {
        var entity = ConciliacionMapper.toEntity(conciliacion);
        return enrich(jpaRepository.save(entity));
    }

    @Override
    public Optional<Conciliacion> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::enrich);
    }

    @Override
    public Optional<Conciliacion> buscarPorPeriodo(String periodo) {
        return jpaRepository.findByPeriodo(periodo).map(this::enrich);
    }

    @Override
    public List<Conciliacion> buscarPorUsuarioCreador(Long idUsuario) {
        return jpaRepository.findByIdUsuarioCreador(idUsuario).stream()
                .map(this::enrich)
                .toList();
    }

    @Override
    public List<Conciliacion> buscarTodas() {
        return jpaRepository.findAll().stream()
                .map(this::enrich)
                .toList();
    }

    @Override
    public boolean existePorPeriodoYCuenta(String periodo, Long idCuenta) {
        return jpaRepository.existsByPeriodoAndIdCuenta(periodo, idCuenta);
    }

    @Override
    public EstadoConciliacion obtenerEstado(Long id) {
        return jpaRepository.findEstadoById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conciliación no encontrada: " + id));
    }

    @Override
    public Long obtenerIdCuentaPorConciliacion(Long idConciliacion) {
        return jpaRepository.findById(idConciliacion)
                .map(ConciliacionEntity::getIdCuenta)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conciliación no encontrada: " + idConciliacion));
    }

    /** Resuelve cuenta → banco para construir el dominio enriquecido */
    private Conciliacion enrich(ConciliacionEntity e) {
        String numeroCuenta = null;
        String tipoCuenta = null;
        Long idBanco = null;
        String nombreBanco = null;

        Optional<CuentaEntity> cuenta = cuentaJpaRepository.findById(e.getIdCuenta());
        if (cuenta.isPresent()) {
            CuentaEntity c = cuenta.get();
            numeroCuenta = c.getNumeroCuenta();
            tipoCuenta = c.getTipo() != null ? c.getTipo().name() : null;
            idBanco = c.getIdBanco();
            nombreBanco = bancoJpaRepository.findById(c.getIdBanco())
                    .map(b -> b.getNombre())
                    .orElse(null);
        }

        return ConciliacionMapper.toDomain(e, numeroCuenta, tipoCuenta, idBanco, nombreBanco);
    }
}
