-- V24: últimos 4 dígitos de la tarjeta de crédito, extraídos del encabezado del extracto
-- (formato ancho fijo -- ver AnchoFijoBankStatementParser). Permite distinguir a qué
-- tarjetahabiente pertenece cada movimiento cuando varios extractos se acumulan en una
-- misma conciliación (auxiliar_conjunto = true). Sólo aplica a movimientos bancarios --
-- los contables no vienen de un extracto de tarjeta.

ALTER TABLE movimientos_bancarios
    ADD COLUMN ultimos_digitos_tarjeta VARCHAR(4) NULL AFTER estado_conciliacion;
