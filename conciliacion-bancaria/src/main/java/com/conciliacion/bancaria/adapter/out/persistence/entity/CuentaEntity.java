package com.conciliacion.bancaria.adapter.out.persistence.entity;

import com.conciliacion.bancaria.shared.TipoCuenta;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cuentas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_banco", nullable = false)
    private Long idBanco;

    @Column(name = "numero_cuenta", nullable = false, length = 50)
    private String numeroCuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCuenta tipo;

    @Column(length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "ts_creacion", nullable = false)
    private LocalDateTime tsCreacion;

    @PrePersist
    public void prePersist() {
        if (tsCreacion == null) tsCreacion = LocalDateTime.now();
        if (activo == null) activo = true;
        if (tipo == null) tipo = TipoCuenta.CORRIENTE;
    }
}
