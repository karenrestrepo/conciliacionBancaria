package com.conciliacion.bancaria.adapter.in.web.dto;

import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PruebaConfiguracionResponse {

    private List<MovimientoPreviewDTO> movimientos;
    private int totalMovimientos;
    private ResultadoCuadre cuadre;
    private List<String> advertencias;
}
