package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.Permiso;
import com.conciliacion.bancaria.shared.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class UsuarioUpdateRequest {

    @NotBlank
    private String nombre;

    @NotBlank
    @Email
    private String email;

    /** Opcional: si viene vacío no se cambia la contraseña. */
    private String password;

    @NotNull
    private Rol rol;

    @NotNull
    private Boolean activo;

    private Set<Permiso> permisos;
}
