package com.conciliacion.bancaria.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConciliacionRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$",
            message = "El período debe tener formato YYYY-MM")
    private String periodo;

    @NotNull(message = "La cuenta es obligatoria")
    private Long idCuenta;
}