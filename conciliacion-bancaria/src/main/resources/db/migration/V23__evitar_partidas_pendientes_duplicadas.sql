-- V23: red de seguridad en BD, además del filtro ya agregado en código
-- (CargaCsvUseCaseImpl.sinPendienteExistente), para el bug donde cada extracto adicional
-- subido en una conciliación con auxiliar_conjunto (tarjetas de crédito) disparaba el motor
-- completo sobre TODOS los bancarios acumulados y volvía a insertar una partida PENDIENTE
-- para movimientos que ya tenían una de una corrida anterior. Ejemplo real: subir 5
-- extractos de tarjeta antes de cargar el auxiliar dejaba al movimiento del primer archivo
-- con hasta 4 partidas PENDIENTE duplicadas.

-- 1) Limpieza de duplicados que ya existan en datos reales (el propio bug que se está
--    arreglando) -- conserva sólo la partida PENDIENTE más antigua (MIN(id)) por cada
--    combinación (conciliación, movimiento, tipo_origen); sin este paso, el ALTER TABLE
--    del punto 2 fallaría contra cualquier base que ya tenga los duplicados del bug.
DELETE p1 FROM partidas_conciliatorias p1
INNER JOIN partidas_conciliatorias p2
    ON p1.id_conciliacion = p2.id_conciliacion
   AND p1.id_mov = p2.id_mov
   AND p1.tipo_origen = p2.tipo_origen
   AND p1.estado = 'PENDIENTE'
   AND p2.estado = 'PENDIENTE'
   AND p1.id > p2.id;

-- 2) MariaDB no soporta índices únicos parciales con WHERE (a diferencia de Postgres) --
--    se simula con una columna GENERADA que sólo tiene valor cuando estado='PENDIENTE'.
--    Un UNIQUE index trata cada NULL como distinto de cualquier otro NULL, así que las
--    filas no-pendientes (JUSTIFICADA/ARRASTRADA/CRUZADA) conviven libremente sin chocar
--    entre sí, y la unicidad real sólo se exige entre las PENDIENTE.
ALTER TABLE partidas_conciliatorias
    ADD COLUMN pendiente_unica TINYINT
        GENERATED ALWAYS AS (CASE WHEN estado = 'PENDIENTE' THEN 1 ELSE NULL END) VIRTUAL;

ALTER TABLE partidas_conciliatorias
    ADD CONSTRAINT uq_partida_pendiente_unica
        UNIQUE (id_conciliacion, id_mov, tipo_origen, pendiente_unica);
