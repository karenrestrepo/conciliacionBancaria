-- V25: limpieza de datos por el bug de anulación de contables (procesarAnulado no borraba
-- la partida del contable dado de baja). Complementa el fix de código en
-- CargaCsvUseCaseImpl.procesarAnulado.
--
-- Bug real reportado (Partida #10013): al anular un comprobante de egreso y reingresarlo con
-- el valor correcto, la recarga del auxiliar daba de baja el movimiento_contable pero NO su
-- partida. Quedaba una fila en partidas_conciliatorias apuntando a un id_mov inexistente y el
-- detalle se renderizaba vacío. El mismo bug de raíz afectó cruces manuales: cuando el
-- contable dado de baja era parte de un cruce (estado CRUZADA), su partida quedaba huérfana Y
-- el/los bancario/s del cruce quedaban CONCILIADO sin contraparte real.
--
-- En la base real esto dejó 47 partidas CONTABLE huérfanas (2 PENDIENTE + 45 CRUZADA en las
-- conciliaciones 16 y 17) y 6 bancarios CRUZADA/CONCILIADO sin ninguna contraparte contable
-- (todo su lado contable fue borrado). Las conciliaciones afectadas están EN_REVISION (no
-- cerradas).

-- ── Paso 1: revertir a PENDIENTE los bancarios de cruces cuyo lado contable quedó
--            completamente huérfano ─────────────────────────────────────────────────────
-- Se identifican por el texto de justificación compartido dentro de la misma conciliación
-- (los cruces históricos no tienen grupo_cruce; esa columna -- ver V26 -- solo puede poblar
-- cruces NUEVOS, el emparejamiento histórico real ya no es reconstruible). La condición es
-- conservadora: solo dispara cuando EXISTE al menos una partida CONTABLE CRUZADA huérfana en
-- el grupo y NO queda ninguna CONTABLE CRUZADA con movimiento vivo -- es decir, el cruce
-- perdió toda su contraparte. Nunca toca cruces parcialmente vivos ni cruces sin lado
-- contable. Debe correr ANTES del Paso 2 (usa las partidas contables huérfanas para detectar
-- el grupo).

-- 1a) el movimiento bancario vuelve a PENDIENTE
UPDATE movimientos_bancarios b
JOIN partidas_conciliatorias pb ON pb.id_mov = b.id AND pb.tipo_origen = 'BANCARIO'
SET b.estado_conciliacion = 'PENDIENTE'
WHERE pb.estado = 'CRUZADA'
  AND EXISTS (
      SELECT 1 FROM partidas_conciliatorias pc
      LEFT JOIN movimientos_contables mc ON mc.id = pc.id_mov
      WHERE pc.id_conciliacion = pb.id_conciliacion
        AND pc.justificacion <=> pb.justificacion
        AND pc.tipo_origen = 'CONTABLE' AND pc.estado = 'CRUZADA'
        AND mc.id IS NULL)
  AND NOT EXISTS (
      SELECT 1 FROM partidas_conciliatorias pc2
      JOIN movimientos_contables mc2 ON mc2.id = pc2.id_mov
      WHERE pc2.id_conciliacion = pb.id_conciliacion
        AND pc2.justificacion <=> pb.justificacion
        AND pc2.tipo_origen = 'CONTABLE' AND pc2.estado = 'CRUZADA');

-- 1b) la partida bancaria vuelve a PENDIENTE y se le limpia la justificación del cruce
UPDATE partidas_conciliatorias pb
SET pb.estado = 'PENDIENTE',
    pb.justificacion = NULL,
    pb.fecha_justificacion = NULL
WHERE pb.tipo_origen = 'BANCARIO' AND pb.estado = 'CRUZADA'
  AND EXISTS (
      SELECT 1 FROM partidas_conciliatorias pc
      LEFT JOIN movimientos_contables mc ON mc.id = pc.id_mov
      WHERE pc.id_conciliacion = pb.id_conciliacion
        AND pc.justificacion <=> pb.justificacion
        AND pc.tipo_origen = 'CONTABLE' AND pc.estado = 'CRUZADA'
        AND mc.id IS NULL)
  AND NOT EXISTS (
      SELECT 1 FROM partidas_conciliatorias pc2
      JOIN movimientos_contables mc2 ON mc2.id = pc2.id_mov
      WHERE pc2.id_conciliacion = pb.id_conciliacion
        AND pc2.justificacion <=> pb.justificacion
        AND pc2.tipo_origen = 'CONTABLE' AND pc2.estado = 'CRUZADA');

-- ── Paso 2: borrar las partidas CONTABLE huérfanas (cualquier estado) ───────────────────
-- Scoping a tipo_origen='CONTABLE' a propósito: las BANCARIO no sufren este bug (los
-- bancarios no se borran en la recarga del auxiliar). Idempotente: en base sana borra 0.
DELETE p FROM partidas_conciliatorias p
LEFT JOIN movimientos_contables m ON m.id = p.id_mov
WHERE p.tipo_origen = 'CONTABLE'
  AND m.id IS NULL;
