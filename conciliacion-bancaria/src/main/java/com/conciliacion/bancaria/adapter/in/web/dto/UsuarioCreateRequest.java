package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.Permiso;
import com.conciliacion.bancaria.shared.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UsuarioCreateRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8)
    private String password;

    @NotNull
    private Rol rol;

    private Set<Permiso> permisos;
}
