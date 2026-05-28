package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.TipoCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CuentaRequest {

    @NotNull(message = "El banco es obligatorio")
    private Long idBanco;

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Size(max = 50, message = "El número de cuenta no puede superar 50 caracteres")
    private String numeroCuenta;

    private TipoCuenta tipo;

    @Size(max = 200, message = "La descripción no puede superar 200 caracteres")
    private String descripcion;
}
