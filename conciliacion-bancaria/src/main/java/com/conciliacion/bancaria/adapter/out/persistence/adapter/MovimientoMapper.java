package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.MovimientoBancarioEntity;
import com.conciliacion.bancaria.adapter.out.persistence.entity.MovimientoContableEntity;
import com.conciliacion.bancaria.domain.model.Movimiento;

public class MovimientoMapper {

    private MovimientoMapper() {}

    public static Movimiento toDomain(MovimientoBancarioEntity e) {
        return Movimiento.builder()
                .id(e.getId())
                .fecha(e.getFecha())
                .descripcion(e.getDescripcion())
                .monto(e.getMonto())
                .tipo(e.getTipo())
                .estado(e.getEstadoConciliacion())
                .ultimosDigitosTarjeta(e.getUltimosDigitosTarjeta())
                .build();
    }

    public static Movimiento toDomain(MovimientoContableEntity e) {
        return Movimiento.builder()
                .id(e.getId())
                .fecha(e.getFecha())
                .descripcion(e.getDescripcion())
                .monto(e.getMonto())
                .tipo(e.getTipo())
                .estado(e.getEstadoConciliacion())
                .numeroComprobante(e.getNumeroComprobante())
                .build();
    }

    public static MovimientoBancarioEntity toBancarioEntity(Movimiento m,
                                                            Long idConciliacion) {
        return MovimientoBancarioEntity.builder()
                .id(m.getId())
                .idConciliacion(idConciliacion)
                .fecha(m.getFecha())
                .descripcion(m.getDescripcion())
                .monto(m.getMonto())
                .tipo(m.getTipo())
                .estadoConciliacion(m.getEstado())
                .ultimosDigitosTarjeta(m.getUltimosDigitosTarjeta())
                .build();
    }

    public static MovimientoContableEntity toContableEntity(Movimiento m,
                                                            Long idConciliacion) {
        return MovimientoContableEntity.builder()
                .id(m.getId())
                .idConciliacion(idConciliacion)
                .fecha(m.getFecha())
                .descripcion(m.getDescripcion())
                .monto(m.getMonto())
                .tipo(m.getTipo())
                .estadoConciliacion(m.getEstado())
                .numeroComprobante(m.getNumeroComprobante())
                .build();
    }
}