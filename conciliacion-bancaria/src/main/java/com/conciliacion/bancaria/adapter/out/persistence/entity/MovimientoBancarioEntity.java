package com.conciliacion.bancaria.adapter.out.persistence.entity;

import com.conciliacion.bancaria.shared.EstadoMovimiento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "movimientos_bancarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoBancarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_conciliacion", nullable = false)
    private Long idConciliacion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_conciliacion", nullable = false)
    private EstadoMovimiento estadoConciliacion;

    @Column(name = "ultimos_digitos_tarjeta", length = 4)
    private String ultimosDigitosTarjeta;

    @PrePersist
    public void prePersist() {
        if (estadoConciliacion == null) estadoConciliacion = EstadoMovimiento.PENDIENTE;
    }
}
