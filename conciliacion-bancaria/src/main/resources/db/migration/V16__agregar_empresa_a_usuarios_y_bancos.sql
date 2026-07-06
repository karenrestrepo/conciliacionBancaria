-- V16: Asociar usuarios y bancos a una empresa (multi-tenancy)
-- El admin semilla (id=1) queda sin empresa (super-admin de sistema).
-- Toda cuenta creada por el flujo de registro tiene empresa_id != NULL.

-- 1. empresa_id en usuarios
ALTER TABLE usuarios
    ADD COLUMN empresa_id BIGINT NULL AFTER id;

ALTER TABLE usuarios
    ADD CONSTRAINT fk_usuario_empresa
        FOREIGN KEY (empresa_id) REFERENCES empresas(id);

CREATE INDEX idx_usuario_empresa ON usuarios(empresa_id);

-- 2. empresa_id en bancos
--    Primero se elimina la unicidad solo por nombre (ahora es por empresa + nombre)
ALTER TABLE bancos
    DROP INDEX uk_banco_nombre;

ALTER TABLE bancos
    ADD COLUMN empresa_id BIGINT NULL AFTER id;

ALTER TABLE bancos
    ADD CONSTRAINT fk_banco_empresa
        FOREIGN KEY (empresa_id) REFERENCES empresas(id);

ALTER TABLE bancos
    ADD CONSTRAINT uk_banco_empresa_nombre UNIQUE (empresa_id, nombre);

CREATE INDEX idx_banco_empresa ON bancos(empresa_id);
