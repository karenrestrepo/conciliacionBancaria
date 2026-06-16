package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.LocalDateTime;

@Getter
@Builder
@With
public class ConfiguracionGastoBancario {

    private final Long id;
    private final Long idCuenta;
    private final String descripcion;
    private final boolean activo;
    private final LocalDateTime fechaCreacion;
}
