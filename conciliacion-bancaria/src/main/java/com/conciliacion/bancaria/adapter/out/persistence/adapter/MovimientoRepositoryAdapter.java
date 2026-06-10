package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoBancarioJpaRepository;
import com.conciliacion.bancaria.adapter.out.persistence.repository.MovimientoContableJpaRepository;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MovimientoRepositoryAdapter implements MovimientoRepositoryPort {

    private final MovimientoBancarioJpaRepository bancarioRepo;
    private final MovimientoContableJpaRepository contableRepo;

    @Override
    public List<Movimiento> guardarBancarios(List<Movimiento> movimientos,
                                             Long idConciliacion) {
        var entities = movimientos.stream()
                .map(m -> MovimientoMapper.toBancarioEntity(m, idConciliacion))
                .toList();
        return bancarioRepo.saveAll(entities).stream()
                .map(MovimientoMapper::toDomain)
                .toList();
    }

    @Override
    public List<Movimiento> guardarContables(List<Movimiento> movimientos,
                                             Long idConciliacion) {
        var entities = movimientos.stream()
                .map(m -> MovimientoMapper.toContableEntity(m, idConciliacion))
                .toList();
        return contableRepo.saveAll(entities).stream()
                .map(MovimientoMapper::toDomain)
                .toList();
    }

    @Override
    public List<Movimiento> buscarBancariosPorConciliacion(Long idConciliacion) {
        return bancarioRepo.findByIdConciliacion(idConciliacion).stream()
                .map(MovimientoMapper::toDomain)
                .toList();
    }

    @Override
    public List<Movimiento> buscarContablesPorConciliacion(Long idConciliacion) {
        return contableRepo.findByIdConciliacion(idConciliacion).stream()
                .map(MovimientoMapper::toDomain)
                .toList();
    }

    @Override
    public void resetEstadosBancarios(Long idConciliacion) {
        bancarioRepo.resetEstados(idConciliacion, com.conciliacion.bancaria.shared.EstadoMovimiento.PENDIENTE);
    }

    @Override
    public void resetEstadosContables(Long idConciliacion) {
        contableRepo.resetEstados(idConciliacion, com.conciliacion.bancaria.shared.EstadoMovimiento.PENDIENTE);
    }

    @Override
    public void resetEstadosBancariosSugeridos(Long idConciliacion) {
        bancarioRepo.resetSugeridos(idConciliacion);
    }

    @Override
    public void resetEstadosContablesSugeridos(Long idConciliacion) {
        contableRepo.resetSugeridos(idConciliacion);
    }

    @Override
    public List<Movimiento> buscarBancariosPendientesPorConciliacion(Long idConciliacion) {
        return bancarioRepo.findPendientesByIdConciliacion(idConciliacion).stream()
                .map(MovimientoMapper::toDomain)
                .toList();
    }

    @Override
    public List<Movimiento> buscarBancariosPorIds(List<Long> ids) {
        return bancarioRepo.findByIdIn(ids).stream()
                .map(MovimientoMapper::toDomain)
                .toList();
    }

    @Override
    public Movimiento actualizar(Movimiento movimiento, Long idConciliacion,
                                 String tipo) {
        if ("BANCARIO".equals(tipo)) {
            var entity = MovimientoMapper.toBancarioEntity(movimiento, idConciliacion);
            return MovimientoMapper.toDomain(bancarioRepo.save(entity));
        }
        var entity = MovimientoMapper.toContableEntity(movimiento, idConciliacion);
        return MovimientoMapper.toDomain(contableRepo.save(entity));
    }
}
