package com.conciliacion.bancaria.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CruzarPartidasRequest {

    /** ID de la partida que inicia el cruce (la del botón "Cruzar" que se pulsó). */
    @NotNull
    private Long idOrigen;

    /**
     * IDs adicionales con los que se cruza el origen.
     * Vacío cuando tipo=GASTO_BANCARIO (solo marca el origen).
     */
    private List<Long> idsDestino = List.of();

    /** GASTO_BANCARIO | CRUZAR */
    @NotNull
    private String tipo;

    public Long getIdOrigen() { return idOrigen; }
    public void setIdOrigen(Long idOrigen) { this.idOrigen = idOrigen; }
    public List<Long> getIdsDestino() { return idsDestino; }
    public void setIdsDestino(List<Long> idsDestino) { this.idsDestino = idsDestino == null ? List.of() : idsDestino; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}
