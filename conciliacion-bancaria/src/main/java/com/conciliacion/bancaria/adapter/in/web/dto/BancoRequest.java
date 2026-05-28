package com.conciliacion.bancaria.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BancoRequest {

    @NotBlank(message = "El nombre del banco es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    @Size(max = 20, message = "El código no puede superar 20 caracteres")
    private String codigo;
}
