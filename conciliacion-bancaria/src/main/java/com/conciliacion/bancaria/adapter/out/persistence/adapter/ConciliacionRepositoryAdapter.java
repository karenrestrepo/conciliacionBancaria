package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.repository.ConciliacionJpaRepository;
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

    @Override
    public Conciliacion guardar(Conciliacion conciliacion) {
        var entity = ConciliacionMapper.toEntity(conciliacion);
        return ConciliacionMapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Conciliacion> buscarPorId(Long id) {
        return jpaRepository.findById(id)
                .map(ConciliacionMapper::toDomain);
    }

    @Override
    public Optional<Conciliacion> buscarPorPeriodo(String periodo) {
        return jpaRepository.findByPeriodo(periodo)
                .map(ConciliacionMapper::toDomain);
    }

    @Override
    public List<Conciliacion> buscarPorUsuarioCreador(Long idUsuario) {
        return jpaRepository.findByIdUsuarioCreador(idUsuario)
                .stream()
                .map(ConciliacionMapper::toDomain)
                .toList();
    }

    @Override
    public List<Conciliacion> buscarTodas() {
        return jpaRepository.findAll().stream()
                .map(ConciliacionMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existePorPeriodo(String periodo) {
        return jpaRepository.existsByPeriodo(periodo);
    }

    @Override
    public EstadoConciliacion obtenerEstado(Long id) {
        return jpaRepository.findEstadoById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conciliación no encontrada: " + id));
    }
}