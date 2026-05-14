-- V6: Plantillas CSV por banco (configurables sin redeploy - TRD §9.1)

CREATE TABLE bank_csv_templates (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_banco   VARCHAR(100) NOT NULL UNIQUE,
    mapeo_columnas JSON         NOT NULL,   -- {"fecha":"date","descripcion":"description",...}
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    ts_creacion    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Plantilla por defecto (columnas estándar del TRD RT-01)
INSERT INTO bank_csv_templates (nombre_banco, mapeo_columnas) VALUES
('GENERICO', '{"fecha":"fecha","descripcion":"descripcion","monto":"monto","tipo_movimiento":"tipo_movimiento"}'),
('BANCOLOMBIA', '{"fecha":"Fecha","descripcion":"Descripcion","monto":"Valor","tipo_movimiento":"Tipo"}'),
('DAVIVIENDA', '{"fecha":"FECHA","descripcion":"CONCEPTO","monto":"MONTO","tipo_movimiento":"TIPO"}');
