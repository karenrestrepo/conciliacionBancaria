package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.TipoCuenta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class CuentaResponse {
    private Long id;
    private Long idBanco;
    private String nombreBanco;
    private String numeroCuenta;
    private TipoCuenta tipo;
    private String descripcion;
    private Boolean activo;
    private Boolean auxiliarConjunto;
    private LocalDateTime tsCreacion;
}
