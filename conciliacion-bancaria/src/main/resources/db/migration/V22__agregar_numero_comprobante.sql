-- V20: Número de comprobante/documento del auxiliar contable (ej. SIESA).
-- Identidad más estable que un hash de texto formateado para detectar duplicados
-- entre recargas del auxiliar — el hash de fecha|monto|tipo|descripcion es frágil
-- ante cambios menores de formato en la descripción re-exportada.

ALTER TABLE movimientos_contables
    ADD COLUMN numero_comprobante VARCHAR(50) NULL AFTER tipo;
