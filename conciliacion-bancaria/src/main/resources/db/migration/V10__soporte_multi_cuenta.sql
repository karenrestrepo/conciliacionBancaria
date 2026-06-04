-- V10: Soporte multi-cuenta por banco
-- Cada banco puede tener varias cuentas (corriente, ahorro, etc.)
-- La unicidad de conciliaciones pasa de (periodo, id_banco)
--                                    a (periodo, id_cuenta)

-- 1. Tabla de cuentas bancarias
CREATE TABLE cuentas (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_banco      BIGINT       NOT NULL,
    numero_cuenta VARCHAR(50)  NOT NULL,
    tipo          VARCHAR(20)  NOT NULL DEFAULT 'CORRIENTE',
    descripcion   VARCHAR(200),
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    ts_creacion   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cuenta_banco        FOREIGN KEY (id_banco) REFERENCES bancos(id),
    CONSTRAINT uk_cuenta_banco_numero UNIQUE (id_banco, numero_cuenta)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cuenta genérica ligada al banco "Sin asignar" para datos preexistentes
INSERT INTO cuentas (id_banco, numero_cuenta, tipo, descripcion)
VALUES (1, 'N/A', 'CORRIENTE', 'Cuenta genérica para migración');

-- 2. Agregar id_cuenta a conciliaciones (DEFAULT 1 migra registros existentes)
ALTER TABLE conciliaciones
    ADD COLUMN id_cuenta BIGINT NOT NULL DEFAULT 1
        AFTER id_banco;

ALTER TABLE conciliaciones
    ADD CONSTRAINT fk_conciliacion_cuenta
        FOREIGN KEY (id_cuenta) REFERENCES cuentas(id);

-- 3. Eliminar TODOS los índices/constraints que referencian id_banco
--    ANTES de eliminar la columna (MariaDB exige este orden)
ALTER TABLE conciliaciones
    DROP FOREIGN KEY fk_conciliacion_banco;

ALTER TABLE conciliaciones
    DROP INDEX uq_conciliacion_periodo_banco;

DROP INDEX idx_conciliaciones_banco ON conciliaciones;

-- 4. Ahora es seguro eliminar la columna
ALTER TABLE conciliaciones
    DROP COLUMN id_banco;

-- 5. Nueva restricción de unicidad y nuevo índice
ALTER TABLE conciliaciones
    ADD CONSTRAINT uq_conciliacion_periodo_cuenta UNIQUE (periodo, id_cuenta);

CREATE INDEX idx_conciliaciones_cuenta ON conciliaciones(id_cuenta);
