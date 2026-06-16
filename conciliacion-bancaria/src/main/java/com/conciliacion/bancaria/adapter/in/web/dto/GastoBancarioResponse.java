package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GastoBancarioResponse {

    private final Long id;
    private final Long idCuenta;
    private final String descripcion;
    private final boolean activo;
    private final LocalDateTime fechaCreacion;
}
