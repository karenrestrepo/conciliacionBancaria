package com.conciliacion.bancaria.adapter.out.persistence.entity;

import com.conciliacion.bancaria.shared.EstadoSugerencia;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sugerencias_conciliacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SugerenciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_conciliacion", nullable = false)
    private Long idConciliacion;

    @Column(name = "id_mov_bancario", nullable = false)
    private Long idMovBancario;

    @Column(name = "id_mov_contable", nullable = false)
    private Long idMovContable;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confianza;

    @Column(nullable = false, length = 100)
    private String criterio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSugerencia estado;

    @PrePersist
    public void prePersist() {
        if (estado == null) estado = EstadoSugerencia.PENDIENTE_REVISION;
    }
}