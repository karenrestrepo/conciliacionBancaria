package com.conciliacion.bancaria.adapter.out.persistence.adapter;

import com.conciliacion.bancaria.adapter.out.persistence.entity.ConciliacionEntity;
import com.conciliacion.bancaria.domain.model.Conciliacion;

public class ConciliacionMapper {

    private ConciliacionMapper() {}

    public static Conciliacion toDomain(ConciliacionEntity e) {
        return toDomain(e, null, null, null, null);
    }

    /**
     * @param numeroCuenta número de cuenta (del JOIN con cuentas)
     * @param tipoCuenta   tipo (CORRIENTE, AHORRO…)
     * @param idBanco      id del banco al que pertenece la cuenta
     * @param nombreBanco  nombre del banco
     */
    public static Conciliacion toDomain(ConciliacionEntity e,
                                        String numeroCuenta,
                                        String tipoCuenta,
                                        Long idBanco,
                                        String nombreBanco) {
        return Conciliacion.builder()
                .id(e.getId())
                .empresaId(e.getEmpresaId())
                .periodo(e.getPeriodo())
                .idCuenta(e.getIdCuenta())
                .numeroCuenta(numeroCuenta)
                .tipoCuenta(tipoCuenta)
                .idBanco(idBanco)
                .nombreBanco(nombreBanco)
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
                .empresaId(c.getEmpresaId())
                .periodo(c.getPeriodo())
                .idCuenta(c.getIdCuenta() != null ? c.getIdCuenta() : 1L)
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
