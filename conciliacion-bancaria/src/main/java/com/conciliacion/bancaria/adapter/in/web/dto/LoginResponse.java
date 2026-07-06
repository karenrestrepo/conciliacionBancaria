package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String email;
    private String rol;
    private Long empresaId;
    private String nombreEmpresa;
    private java.util.List<String> permisos;
    private long expiresIn;
}