package com.conciliacion.bancaria.adapter.out.persistence.entity;

import com.conciliacion.bancaria.shared.Permiso;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "usuario_permisos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioPermisoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Permiso permiso;
}
