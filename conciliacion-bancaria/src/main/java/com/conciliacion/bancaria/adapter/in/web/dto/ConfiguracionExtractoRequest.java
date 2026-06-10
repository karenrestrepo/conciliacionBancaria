package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.TipoArchivoExtracto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ConfiguracionExtractoRequest {

    @NotNull(message = "El banco es obligatorio")
    private Long idBanco;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "El tipo de archivo es obligatorio")
    private TipoArchivoExtracto tipoArchivo;

    private boolean aplicaParaTodasLasCuentas = true;

    private List<Long> idsCuentas;

    private ConfiguracionDetalleDTO configuracionDetalle;
}
