package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.SugerenciaEntity;
import com.conciliacion.bancaria.adapter.out.persistence.repository.*;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.Sugerencia;
import com.conciliacion.bancaria.domain.port.out.SugerenciaRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SugerenciaRepositoryAdapter implements SugerenciaRepositoryPort {

    private final SugerenciaJpaRepository jpaRepository;
    private final MovimientoBancarioJpaRepository bancarioRepo;
    private final MovimientoContableJpaRepository contableRepo;

    @Override
    public List<Sugerencia> guardarTodas(List<Sugerencia> sugerencias) {
        var entities = sugerencias.stream()
                .map(this::toEntity)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Sugerencia> buscarPorConciliacion(Long idConciliacion) {
        return jpaRepository.findByIdConciliacion(idConciliacion).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Sugerencia> buscarPorId(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Sugerencia actualizar(Sugerencia sugerencia) {
        return toDomain(jpaRepository.save(toEntity(sugerencia)));
    }

    @Override
    public void eliminarPorConciliacion(Long idConciliacion) {
        jpaRepository.deleteByIdConciliacion(idConciliacion);
    }

    private SugerenciaEntity toEntity(Sugerencia s) {
        return SugerenciaEntity.builder()
                .id(s.getId())
                .idConciliacion(s.getIdConciliacion())
                .idMovBancario(s.getMovimientoBancario().getId())
                .idMovContable(s.getMovimientoContable().getId())
                .confianza(s.getConfianza())
                .criterio(s.getCriterio())
                .estado(s.getEstado())
                .build();
    }

    private Sugerencia toDomain(SugerenciaEntity e) {
        Movimiento bancario = bancarioRepo.findById(e.getIdMovBancario())
                .map(MovimientoMapper::toDomain)
                .orElseThrow();
        Movimiento contable = contableRepo.findById(e.getIdMovContable())
                .map(MovimientoMapper::toDomain)
                .orElseThrow();
        return Sugerencia.builder()
                .id(e.getId())
                .idConciliacion(e.getIdConciliacion())
                .movimientoBancario(bancario)
                .movimientoContable(contable)
                .confianza(e.getConfianza())
                .criterio(e.getCriterio())
                .estado(e.getEstado())
                .build();
    }
}
