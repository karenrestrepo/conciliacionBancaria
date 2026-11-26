-- V20: Elimina bank_csv_templates (V6), tabla huérfana.
-- Nunca tuvo entidad JPA ni repositorio Java: fue el primer intento de
-- "configuración de extracto por banco", abandonado sin limpiar cuando
-- V11 creó configuraciones_extracto (la que sí se usa en producción).

DROP TABLE IF EXISTS bank_csv_templates;
