package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.TipoIdentificacion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistroRequest {

    // Datos de la empresa
    @NotNull
    private TipoIdentificacion tipoIdentificacion;

    @NotBlank
    private String numeroIdentificacion;

    private String digitoVerificacion;

    @NotBlank
    private String nombreEmpresa;

    // Datos del admin
    @NotBlank
    private String nombreAdmin;

    @NotBlank
    @Email
    private String emailAdmin;

    @NotBlank
    @Size(min = 8)
    private String passwordAdmin;
}
