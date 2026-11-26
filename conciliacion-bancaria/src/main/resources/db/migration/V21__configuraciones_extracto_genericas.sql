-- V21: Siembra una configuración de extracto "Genérico (migración automática)" para
-- cada banco que aún no tenga ninguna configuracion_extracto propia.
--
-- Antes de este cambio, cargarExtractoBancario tenía un fallback implícito para CSV
-- (MAPEO_ESTANDAR: columnas "fecha","descripcion","monto","tipo_movimiento" por nombre
-- de encabezado) cuando no existía ninguna ConfiguracionExtracto activa. Ese fallback
-- se elimina: ahora SIEMPRE se exige una configuración activa (igual que ya exigía
-- XLSX). Esta migración reproduce el mismo comportamiento de forma explícita para no
-- romper conciliaciones en curso de bancos que nunca configuraron su extracto.
--
-- tipoOrigen=DELIMITADO con columnaTipoMovimiento (monto sin signo + columna literal
-- "DEBITO"/"CREDITO"), replicando exactamente MAPEO_ESTANDAR: columnas en orden
-- fecha(0), descripcion(1), monto(2), tipo_movimiento(3), fecha en formato yyyy-MM-dd.

INSERT INTO configuraciones_extracto
    (id_banco, nombre, tipo_archivo, aplica_para_todas_las_cuentas, configuracion_detalle, activo, fecha_creacion)
SELECT
    b.id,
    'Genérico (migración automática)',
    'CSV',
    TRUE,
    '{"tipoOrigen":"DELIMITADO","encoding":"UTF-8","factorMonto":1,"separadorMiles":"","separadorDecimales":".","delimitado":{"separador":",","filasASaltar":1,"columnaFecha":0,"formatoFecha":"yyyy-MM-dd","columnaDescripcion":1,"columnaReferencia":-1,"columnaMonto":2,"columnaTipoMovimiento":3},"continuacion":{"habilitada":false},"cuadre":{"habilitada":false}}',
    TRUE,
    NOW()
FROM bancos b
WHERE NOT EXISTS (
    SELECT 1 FROM configuraciones_extracto ce WHERE ce.id_banco = b.id
);
