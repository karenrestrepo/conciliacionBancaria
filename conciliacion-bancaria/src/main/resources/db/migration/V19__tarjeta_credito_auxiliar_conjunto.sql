-- V19: Soporte para tarjetas de crédito con auxiliar conjunto.
-- Las tarjetas de crédito de un mismo banco comparten un único auxiliar contable
-- (diferenciado por tercero/banco), por lo que permiten múltiples extractos por conciliación.

ALTER TABLE cuentas
    ADD COLUMN IF NOT EXISTS auxiliar_conjunto BOOLEAN NOT NULL DEFAULT FALSE;

-- Índice para filtrar cuentas con auxiliar conjunto de forma eficiente
CREATE INDEX IF NOT EXISTS idx_cuentas_auxiliar_conjunto
    ON cuentas (auxiliar_conjunto);
