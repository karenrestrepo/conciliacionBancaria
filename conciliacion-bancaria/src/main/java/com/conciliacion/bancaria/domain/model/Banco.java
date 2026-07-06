package com.conciliacion.bancaria.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.LocalDateTime;

@Getter
@Builder
@With
public class Banco {

    private final Long id;
    private final Long empresaId;
    private final String nombre;
    private final String codigo;
    private final Boolean activo;
    private final LocalDateTime tsCreacion;
}
