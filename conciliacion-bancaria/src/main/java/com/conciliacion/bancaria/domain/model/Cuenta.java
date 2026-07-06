package com.conciliacion.bancaria.domain.model;

import com.conciliacion.bancaria.shared.TipoCuenta;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.LocalDateTime;

@Getter
@Builder
@With
public class Cuenta {

    private final Long id;
    private final Long idBanco;
    private final String nombreBanco;
    private final String numeroCuenta;
    private final TipoCuenta tipo;
    private final String descripcion;
    private final Boolean activo;
    private final Boolean auxiliarConjunto;
    private final LocalDateTime tsCreacion;
}
