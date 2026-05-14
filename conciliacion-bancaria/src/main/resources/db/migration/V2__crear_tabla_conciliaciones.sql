-- V2: Conciliaciones (registro maestro de cada proceso mensual)
-- Estado: BORRADOR → EN_REVISION → CERRADA (máquina de estados en dominio)

CREATE TABLE conciliaciones (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    periodo              VARCHAR(7)  NOT NULL,   -- formato: YYYY-MM
    estado               ENUM('BORRADOR','EN_REVISION','CERRADA') NOT NULL DEFAULT 'BORRADOR',
    id_usuario_creador   BIGINT      NOT NULL,
    id_usuario_aprobador BIGINT      NULL,        -- se llena al cerrar
    ts_creacion          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ts_cierre            DATETIME    NULL,
    saldo_extracto       DECIMAL(18,2) NULL,
    saldo_auxiliar       DECIMAL(18,2) NULL,
    diferencia_saldo     DECIMAL(18,2) NULL,
    CONSTRAINT fk_conc_creador   FOREIGN KEY (id_usuario_creador)   REFERENCES usuarios(id),
    CONSTRAINT fk_conc_aprobador FOREIGN KEY (id_usuario_aprobador) REFERENCES usuarios(id),
    CONSTRAINT uq_conciliacion_periodo UNIQUE (periodo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_conciliaciones_periodo ON conciliaciones(periodo);
CREATE INDEX idx_conciliaciones_estado  ON conciliaciones(estado);
