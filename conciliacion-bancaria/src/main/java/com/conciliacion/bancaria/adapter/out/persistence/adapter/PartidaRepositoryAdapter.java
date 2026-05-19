package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.PartidaEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.PartidaJpaRepository;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PartidaRepositoryAdapter implements PartidaRepositoryPort {

    private final PartidaJpaRepository jpaRepository;

    @Override
    public PartidaConciliatoria guardar(PartidaConciliatoria partida) {
        return toDomain(jpaRepository.save(toEntity(partida)));
    }

    @Override
    public List<PartidaConciliatoria> guardarTodas(List<PartidaConciliatoria> partidas) {
        return jpaRepository.saveAll(partidas.stream().map(this::toEntity).toList())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<PartidaConciliatoria> buscarPorConciliacion(Long idConciliacion) {
        return jpaRepository.findByIdConciliacion(idConciliacion)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<PartidaConciliatoria> buscarPendientesSinJustificar(Long idConciliacion) {
        return jpaRepository.findPendientesSinJustificar(idConciliacion)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PartidaConciliatoria> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PartidaConciliatoria actualizar(PartidaConciliatoria partida) {
        return toDomain(jpaRepository.save(toEntity(partida)));
    }

    private PartidaEntity toEntity(PartidaConciliatoria p) {
        return PartidaEntity.builder()
                .id(p.getId())
                .idConciliacion(p.getIdConciliacion())
                .idMovimiento(p.getIdMovimiento())
                .tipoOrigen(p.getTipoOrigen())
                .fechaJustificacion(p.getFechaJustificacion())
                .justificacion(p.getJustificacion())
                .estado(p.getEstado())
                .periodoArrastre(p.getPeriodoArrastre())
                .build();
    }

    private PartidaConciliatoria toDomain(PartidaEntity e) {
        return PartidaConciliatoria.builder()
                .id(e.getId())
                .idConciliacion(e.getIdConciliacion())
                .idMovimiento(e.getIdMovimiento())
                .tipoOrigen(e.getTipoOrigen())
                .fechaJustificacion(e.getFechaJustificacion())
                .justificacion(e.getJustificacion())
                .estado(e.getEstado())
                .periodoArrastre(e.getPeriodoArrastre())
                .build();
    }
}
