-- V26: columna de emparejamiento real para cruces manuales.
--
-- Hasta ahora las partidas de un mismo cruce manual solo compartían el texto de
-- justificacion (un default como "Cruzado manualmente"), que se repite entre cruces
-- independientes y NO permite saber qué partidas pertenecen al mismo cruce. Por eso, al
-- anular un contable cruzado, no había forma confiable de revertir exactamente a los
-- bancarios de SU cruce (ver bug de partidas huérfanas, V25).
--
-- grupo_cruce guarda un identificador (UUID) que ClosureService.cruzarPartidas asigna a
-- todas las partidas de un mismo cruce nuevo. Nullable: las partidas que no vienen de un
-- cruce manual (PENDIENTE, JUSTIFICADA, ARRASTRADA) lo dejan en NULL, y los cruces
-- históricos previos a esta columna quedan en NULL (su emparejamiento real ya no es
-- reconstruible).
ALTER TABLE partidas_conciliatorias
    ADD COLUMN grupo_cruce VARCHAR(36) NULL;

CREATE INDEX idx_partida_grupo_cruce ON partidas_conciliatorias(grupo_cruce);
