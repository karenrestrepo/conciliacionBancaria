package com.conciliacion.bancaria.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "conciliacion_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConciliacionJobEntity {

    @Id
    private String id;  // UUID

    @Column(name = "id_conciliacion", nullable = false)
    private Long idConciliacion;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(nullable = false)
    private Integer progreso;

    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;

    @Column(name = "ts_inicio", nullable = false)
    private LocalDateTime tsInicio;

    @Column(name = "ts_fin")
    private LocalDateTime tsFin;

    @PrePersist
    public void prePersist() {
        if (tsInicio == null) tsInicio = LocalDateTime.now();
        if (progreso == null) progreso = 0;
        if (estado == null) estado = "PENDING";
    }
}
