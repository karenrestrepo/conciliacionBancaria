package com.conciliacion.bancaria.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GastoBancarioRequest {

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 300, message = "La descripción no puede superar 300 caracteres")
    private String descripcion;
}
