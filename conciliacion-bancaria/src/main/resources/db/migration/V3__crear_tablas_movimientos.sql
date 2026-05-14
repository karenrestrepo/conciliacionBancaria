-- V3: Movimientos bancarios (cargados desde CSV extracto)
--     Movimientos contables (cargados desde CSV libro auxiliar - stub del sistema contable)

CREATE TABLE movimientos_bancarios (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_conciliacion      BIGINT          NOT NULL,
    fecha                DATE            NOT NULL,
    descripcion          VARCHAR(255)    NOT NULL,
    monto                DECIMAL(18,2)   NOT NULL,
    tipo                 ENUM('DEBITO','CREDITO') NOT NULL,
    estado_conciliacion  ENUM('PENDIENTE','SUGERIDO','CONCILIADO') NOT NULL DEFAULT 'PENDIENTE',
    CONSTRAINT fk_movbanco_conc FOREIGN KEY (id_conciliacion) REFERENCES conciliaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_movbanco_conciliacion ON movimientos_bancarios(id_conciliacion);
CREATE INDEX idx_movbanco_monto        ON movimientos_bancarios(monto);
CREATE INDEX idx_movbanco_fecha        ON movimientos_bancarios(fecha);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE movimientos_contables (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_conciliacion      BIGINT          NOT NULL,
    fecha                DATE            NOT NULL,
    descripcion          VARCHAR(255)    NOT NULL,
    monto                DECIMAL(18,2)   NOT NULL,
    tipo                 ENUM('DEBITO','CREDITO') NOT NULL,
    estado_conciliacion  ENUM('PENDIENTE','SUGERIDO','CONCILIADO') NOT NULL DEFAULT 'PENDIENTE',
    CONSTRAINT fk_movcont_conc FOREIGN KEY (id_conciliacion) REFERENCES conciliaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_movcont_conciliacion ON movimientos_contables(id_conciliacion);
CREATE INDEX idx_movcont_monto        ON movimientos_contables(monto);
