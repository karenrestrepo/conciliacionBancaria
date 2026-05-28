package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class BancoResponse {
    private Long id;
    private String nombre;
    private String codigo;
    private Boolean activo;
    private LocalDateTime tsCreacion;
}
