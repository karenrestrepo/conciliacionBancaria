package com.conciliacion.bancaria.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "partidas_conciliatorias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartidaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_conciliacion", nullable = false)
    private Long idConciliacion;

    @Column(name = "id_mov", nullable = false)
    private Long idMovimiento;

    @Column(name = "tipo_origen", nullable = false, length = 10)
    private String tipoOrigen;

    @Column(name = "fecha_justificacion")
    private LocalDate fechaJustificacion;

    @Column(columnDefinition = "TEXT")
    private String justificacion;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "periodo_arrastre", length = 7)
    private String periodoArrastre;

    @Column(name = "grupo_cruce", length = 36)
    private String grupoCruce;

    @PrePersist
    public void prePersist() {
        if (estado == null) estado = "PENDIENTE";
    }
}