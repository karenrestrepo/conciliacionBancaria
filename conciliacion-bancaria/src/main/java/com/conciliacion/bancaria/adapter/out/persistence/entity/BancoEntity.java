package com.conciliacion.bancaria.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bancos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BancoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String nombre;

    @Column(length = 20)
    private String codigo;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "ts_creacion", nullable = false)
    private LocalDateTime tsCreacion;

    @PrePersist
    public void prePersist() {
        if (tsCreacion == null) tsCreacion = LocalDateTime.now();
        if (activo == null) activo = true;
    }
}
