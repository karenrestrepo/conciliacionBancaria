-- V4: Sugerencias del motor de conciliación y partidas conciliatorias

CREATE TABLE sugerencias_conciliacion (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_conciliacion   BIGINT          NOT NULL,
    id_mov_bancario   BIGINT          NOT NULL,
    id_mov_contable   BIGINT          NOT NULL,
    confianza         DECIMAL(5,4)    NOT NULL,   -- 0.0000 a 1.0000
    criterio          VARCHAR(100)    NOT NULL,   -- ej: "MONTO_EXACTO", "MONTO_FECHA_PROXIMA"
    estado            ENUM('PENDIENTE_REVISION','ACEPTADA','RECHAZADA','REASIGNADA') NOT NULL DEFAULT 'PENDIENTE_REVISION',
    CONSTRAINT fk_sug_conc        FOREIGN KEY (id_conciliacion) REFERENCES conciliaciones(id),
    CONSTRAINT fk_sug_movbanco    FOREIGN KEY (id_mov_bancario) REFERENCES movimientos_bancarios(id),
    CONSTRAINT fk_sug_movcont     FOREIGN KEY (id_mov_contable) REFERENCES movimientos_contables(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sugerencias_conciliacion ON sugerencias_conciliacion(id_conciliacion);
CREATE INDEX idx_sugerencias_estado       ON sugerencias_conciliacion(estado);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE partidas_conciliatorias (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_conciliacion       BIGINT          NOT NULL,
    id_mov                BIGINT          NOT NULL,
    tipo_origen           ENUM('BANCARIO','CONTABLE') NOT NULL,
    fecha_justificacion   DATE            NULL,
    justificacion         TEXT            NULL,
    estado                ENUM('PENDIENTE','JUSTIFICADA','ARRASTRADA') NOT NULL DEFAULT 'PENDIENTE',
    periodo_arrastre      VARCHAR(7)      NULL,    -- formato YYYY-MM, lleno si estado=ARRASTRADA
    CONSTRAINT fk_partida_conc FOREIGN KEY (id_conciliacion) REFERENCES conciliaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_partidas_conciliacion ON partidas_conciliatorias(id_conciliacion);
CREATE INDEX idx_partidas_estado       ON partidas_conciliatorias(estado);
