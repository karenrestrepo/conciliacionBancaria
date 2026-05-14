-- V5: Métricas de conciliación (KPIs y dashboard - TRD §7.5)

CREATE TABLE metricas_conciliacion (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_conciliacion   BIGINT          NOT NULL UNIQUE,
    id_usuario        BIGINT          NOT NULL,
    t_inicio          DATETIME        NOT NULL,
    t_cierre          DATETIME        NULL,
    n_total           INT             NOT NULL DEFAULT 0,
    n_auto            INT             NOT NULL DEFAULT 0,   -- conciliados automáticamente
    n_manual          INT             NOT NULL DEFAULT 0,   -- aceptados/rechazados manualmente
    n_pendiente       INT             NOT NULL DEFAULT 0,   -- partidas sin resolver
    saldo_extracto    DECIMAL(18,2)   NULL,
    saldo_auxiliar    DECIMAL(18,2)   NULL,
    diferencia_saldo  DECIMAL(18,2)   NULL,
    CONSTRAINT fk_metricas_conc    FOREIGN KEY (id_conciliacion) REFERENCES conciliaciones(id),
    CONSTRAINT fk_metricas_usuario FOREIGN KEY (id_usuario)      REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_metricas_conciliacion ON metricas_conciliacion(id_conciliacion);
CREATE INDEX idx_metricas_usuario      ON metricas_conciliacion(id_usuario);
