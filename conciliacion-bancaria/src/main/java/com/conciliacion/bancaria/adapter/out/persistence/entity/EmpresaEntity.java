package com.conciliacion.bancaria.adapter.out.persistence.entity;

import com.conciliacion.bancaria.shared.TipoIdentificacion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpresaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_identificacion", nullable = false)
    private TipoIdentificacion tipoIdentificacion;

    @Column(name = "numero_identificacion", nullable = false, length = 20)
    private String numeroIdentificacion;

    @Column(name = "digito_verificacion", length = 1)
    private String digitoVerificacion;

    @Column(nullable = false, length = 200)
    private String nombre;

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
