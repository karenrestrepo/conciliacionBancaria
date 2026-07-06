package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class ConciliacionResponse {
    private Long id;
    private String periodo;
    private Long idCuenta;
    private String numeroCuenta;
    private String tipoCuenta;
    private Long idBanco;
    private String nombreBanco;
    private String estado;
    private Long idUsuarioCreador;
    private Long idUsuarioAprobador;
    private LocalDateTime tsCreacion;
    private LocalDateTime tsCierre;
    private BigDecimal saldoExtracto;
    private BigDecimal saldoAuxiliar;
    private BigDecimal diferenciaSaldo;
    private Boolean auxiliarConjunto;
}