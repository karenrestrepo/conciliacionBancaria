-- V18: empresa_id en conciliaciones para aislamiento directo de datos.
-- NOTA: V10 eliminó id_banco de conciliaciones y lo reemplazó con id_cuenta.
--       Por eso la unicidad es (empresa_id, periodo, id_cuenta).
--       La constraint anterior a reemplazar es uq_conciliacion_periodo_cuenta.
-- Usa PREPARE/EXECUTE para que sea idempotente ante ejecuciones parciales.

-- 1. Agregar columna si no existe
ALTER TABLE conciliaciones
    ADD COLUMN IF NOT EXISTS empresa_id BIGINT NULL AFTER id;

-- 2. FK: agregar solo si no existe
SET @fk_existe = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'conciliaciones'
      AND CONSTRAINT_NAME = 'fk_conciliacion_empresa'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql_fk = IF(@fk_existe > 0,
    'SELECT 1',
    'ALTER TABLE conciliaciones ADD CONSTRAINT fk_conciliacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id)'
);
PREPARE _stmt FROM @sql_fk;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

-- 3. Eliminar restriccion de unicidad anterior (uq_conciliacion_periodo_cuenta, creada en V10)
SET @uq_vieja = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'conciliaciones'
      AND CONSTRAINT_NAME = 'uq_conciliacion_periodo_cuenta'
      AND CONSTRAINT_TYPE = 'UNIQUE'
);
SET @sql_drop = IF(@uq_vieja > 0,
    'DROP INDEX uq_conciliacion_periodo_cuenta ON conciliaciones',
    'SELECT 1'
);
PREPARE _stmt FROM @sql_drop;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

-- 4. Nueva restriccion de unicidad: empresa + periodo + cuenta
SET @uq_nueva = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'conciliaciones'
      AND CONSTRAINT_NAME = 'uq_conciliacion_empresa_periodo_cuenta'
      AND CONSTRAINT_TYPE = 'UNIQUE'
);
SET @sql_uq = IF(@uq_nueva > 0,
    'SELECT 1',
    'ALTER TABLE conciliaciones ADD CONSTRAINT uq_conciliacion_empresa_periodo_cuenta UNIQUE (empresa_id, periodo, id_cuenta)'
);
PREPARE _stmt FROM @sql_uq;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

-- 5. Indice de busqueda por empresa
CREATE INDEX IF NOT EXISTS idx_conciliaciones_empresa ON conciliaciones(empresa_id);
