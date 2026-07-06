package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.Permiso;
import com.conciliacion.bancaria.shared.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponse {
    private Long id;
    private String nombre;
    private String email;
    private Rol rol;
    private Boolean activo;
    private LocalDateTime tsCreacion;
    private Set<Permiso> permisos;
}
