-- V7: Jobs asíncronos del motor de conciliación (patrón Polling - TRD RT-03)
-- El frontend consulta GET /api/v1/conciliaciones/jobs/{job_id}/status cada 2s

CREATE TABLE conciliacion_jobs (
    id               VARCHAR(36) PRIMARY KEY,   -- UUID
    id_conciliacion  BIGINT      NOT NULL,
    estado           ENUM('PENDING','IN_PROGRESS','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
    progreso         INT         NOT NULL DEFAULT 0,   -- 0-100
    mensaje_error    TEXT        NULL,
    ts_inicio        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ts_fin           DATETIME    NULL,
    CONSTRAINT fk_job_conciliacion FOREIGN KEY (id_conciliacion) REFERENCES conciliaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_jobs_conciliacion ON conciliacion_jobs(id_conciliacion);
CREATE INDEX idx_jobs_estado       ON conciliacion_jobs(estado);
