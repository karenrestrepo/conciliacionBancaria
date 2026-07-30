package com.conciliacion.bancaria.adapter.in.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResumenCargaAuxiliarResponse {

    private String jobId;
    private int nuevos;
    private int anulados;
    private int revertidos;
}
