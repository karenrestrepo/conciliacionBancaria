package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.shared.TipoArchivoExtracto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ConfiguracionExtractoResponse {

    private Long id;
    private Long idBanco;
    private String nombreBanco;
    private String nombre;
    private TipoArchivoExtracto tipoArchivo;
    private boolean aplicaParaTodasLasCuentas;
    private List<Long> idsCuentas;
    private String configuracionDetalle;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
}
