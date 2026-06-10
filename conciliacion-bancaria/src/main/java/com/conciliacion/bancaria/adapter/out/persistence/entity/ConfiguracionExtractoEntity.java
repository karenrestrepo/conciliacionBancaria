package com.conciliacion.bancaria.adapter.out.persistence.entity;

import com.conciliacion.bancaria.shared.TipoArchivoExtracto;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "configuraciones_extracto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionExtractoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_banco", nullable = false)
    private Long idBanco;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_archivo", nullable = false, length = 10)
    private TipoArchivoExtracto tipoArchivo;

    @Column(name = "aplica_para_todas_las_cuentas", nullable = false)
    private boolean aplicaParaTodasLasCuentas;

    @ElementCollection
    @CollectionTable(
            name = "configuraciones_extracto_cuentas",
            joinColumns = @JoinColumn(name = "id_configuracion")
    )
    @Column(name = "id_cuenta")
    @Builder.Default
    private Set<Long> idsCuentas = new HashSet<>();

    @Column(name = "configuracion_detalle", columnDefinition = "TEXT")
    private String configuracionDetalle;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
        if (activo == null) activo = true;
    }

    @PreUpdate
    public void preUpdate() {
        fechaModificacion = LocalDateTime.now();
    }
}
