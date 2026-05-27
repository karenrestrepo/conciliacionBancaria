package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class PartidaResponse {
    private Long id;
    private Long idConciliacion;
    private Long idMovimiento;
    private String tipoOrigen;
    private String estado;
    private String justificacion;
    private LocalDate fechaJustificacion;
}
