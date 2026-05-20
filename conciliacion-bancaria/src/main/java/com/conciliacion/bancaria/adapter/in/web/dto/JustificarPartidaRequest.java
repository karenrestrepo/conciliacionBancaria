package com.conciliacion.bancaria.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class JustificarPartidaRequest {

    @NotBlank
    private String justificacion;

    @NotNull
    private LocalDate fecha;
}