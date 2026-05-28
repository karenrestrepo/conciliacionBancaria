package com.conciliacion.bancaria.adapter.out.persistence.entity;

import com.conciliacion.bancaria.shared.EstadoConciliacion;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "conciliaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConciliacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 7)
    private String periodo;

    @Column(name = "id_cuenta", nullable = false)
    private Long idCuenta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConciliacion estado;

    @Column(name = "id_usuario_creador", nullable = false)
    private Long idUsuarioCreador;

    @Column(name = "id_usuario_aprobador")
    private Long idUsuarioAprobador;

    @Column(name = "ts_creacion", nullable = false)
    private LocalDateTime tsCreacion;

    @Column(name = "ts_cierre")
    private LocalDateTime tsCierre;

    @Column(name = "saldo_extracto", precision = 18, scale = 2)
    private BigDecimal saldoExtracto;

    @Column(name = "saldo_auxiliar", precision = 18, scale = 2)
    private BigDecimal saldoAuxiliar;

    @Column(name = "diferencia_saldo", precision = 18, scale = 2)
    private BigDecimal diferenciaSaldo;

    @PrePersist
    public void prePersist() {
        if (tsCreacion == null) tsCreacion = LocalDateTime.now();
        if (estado == null) estado = EstadoConciliacion.BORRADOR;
    }
}