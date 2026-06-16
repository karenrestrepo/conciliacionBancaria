-- V12: Tabla de configuración de gastos bancarios agrupables por descripción
-- Los gastos bancarios (4x1000, comisiones, etc.) aparecen individualmente en el
-- extracto pero se contabilizan como una sola nota en el auxiliar. Esta tabla
-- permite configurar qué descripciones se agrupan antes de la conciliación.

CREATE TABLE configuracion_gasto_bancario (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    id_cuenta       BIGINT          NOT NULL,
    descripcion     VARCHAR(300)    NOT NULL,
    activo          BOOLEAN         NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT fk_cgb_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuentas(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
