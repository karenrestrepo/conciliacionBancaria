package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConciliacionEntity;
import com.conciliacion.bancaria.domain.model.Conciliacion;

public class ConciliacionMapper {

    private ConciliacionMapper() {}

    public static Conciliacion toDomain(ConciliacionEntity e) {
        return Conciliacion.builder()
                .id(e.getId())
                .periodo(e.getPeriodo())
                .estado(e.getEstado())
                .idUsuarioCreador(e.getIdUsuarioCreador())
                .idUsuarioAprobador(e.getIdUsuarioAprobador())
                .tsCreacion(e.getTsCreacion())
                .tsCierre(e.getTsCierre())
                .saldoExtracto(e.getSaldoExtracto())
                .saldoAuxiliar(e.getSaldoAuxiliar())
                .diferenciaSaldo(e.getDiferenciaSaldo())
                .build();
    }

    public static ConciliacionEntity toEntity(Conciliacion c) {
        return ConciliacionEntity.builder()
                .id(c.getId())
                .periodo(c.getPeriodo())
                .estado(c.getEstado())
                .idUsuarioCreador(c.getIdUsuarioCreador())
                .idUsuarioAprobador(c.getIdUsuarioAprobador())
                .tsCreacion(c.getTsCreacion())
                .tsCierre(c.getTsCierre())
                .saldoExtracto(c.getSaldoExtracto())
                .saldoAuxiliar(c.getSaldoAuxiliar())
                .diferenciaSaldo(c.getDiferenciaSaldo())
                .build();
    }
}