package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.TipoIdentificacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerificarEmpresaRequest {

    @NotNull
    private TipoIdentificacion tipoIdentificacion;

    @NotBlank
    private String numeroIdentificacion;

    private String digitoVerificacion;
}
