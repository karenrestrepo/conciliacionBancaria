package com.conciliacion.bancaria.domain.model;

import com.conciliacion.bancaria.shared.TipoArchivoExtracto;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@With
public class ConfiguracionExtracto {

    private final Long id;
    private final Long idBanco;
    private final String nombreBanco;
    private final String nombre;
    private final TipoArchivoExtracto tipoArchivo;
    private final boolean aplicaParaTodasLasCuentas;
    private final List<Long> idsCuentas;
    private final String configuracionDetalle;
    private final Boolean activo;
    private final LocalDateTime fechaCreacion;
    private final LocalDateTime fechaModificacion;
}
