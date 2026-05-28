-- V9: Soporte multi-banco
-- Cada conciliación queda asociada a un banco específico.
-- La unicidad cambia de (periodo) a (periodo, id_banco).

-- 1. Tabla de bancos (creados según necesidad del cliente)
CREATE TABLE bancos (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(150) NOT NULL,
    codigo      VARCHAR(20),
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    ts_creacion DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_banco_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Banco genérico para conciliaciones preexistentes
INSERT INTO bancos (nombre, codigo) VALUES ('Sin asignar', 'N/A');

-- 2. Agregar columna id_banco a conciliaciones
ALTER TABLE conciliaciones
    ADD COLUMN id_banco BIGINT NOT NULL DEFAULT 1
        AFTER periodo;

ALTER TABLE conciliaciones
    ADD CONSTRAINT fk_conciliacion_banco
        FOREIGN KEY (id_banco) REFERENCES bancos(id);

-- 3. Sustituir unicidad: antes era solo por periodo, ahora es (periodo, banco)
ALTER TABLE conciliaciones
    DROP INDEX uq_conciliacion_periodo;

ALTER TABLE conciliaciones
    ADD CONSTRAINT uq_conciliacion_periodo_banco UNIQUE (periodo, id_banco);

-- 4. Índice compuesto para consultas frecuentes
CREATE INDEX idx_conciliaciones_banco ON conciliaciones(id_banco);
