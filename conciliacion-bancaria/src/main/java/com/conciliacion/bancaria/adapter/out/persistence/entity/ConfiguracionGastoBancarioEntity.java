package com.conciliacion.bancaria.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracion_gasto_bancario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionGastoBancarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_cuenta", nullable = false)
    private Long idCuenta;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
        activo = true;
    }
}
