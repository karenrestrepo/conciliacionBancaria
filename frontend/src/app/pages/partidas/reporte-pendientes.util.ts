import { Conciliacion, Partida } from '../../core/models';

/** Filtros elegidos en ReportePendientesDialogComponent. */
export interface FiltrosReportePendientes {
  debito: boolean;
  credito: boolean;
  bancario: boolean;
  contable: boolean;
  incluirHistoricas: boolean;
}

/**
 * Combina las partidas del período actual con las históricas (si aplica) y aplica los
 * filtros de débito/crédito y bancario/contable sobre el conjunto combinado.
 *
 * Decisión de diseño (checkbox vacío = sin filtrar esa dimensión, no "resultado vacío"):
 * si el usuario no marca ningún checkbox de una dimensión (ni débito ni crédito, o ni
 * bancario ni contable), se trata como si no hubiera filtro para esa dimensión -- es decir,
 * como si estuvieran ambos marcados. La alternativa (forzar con validación a marcar al
 * menos uno) obligaría a bloquear el botón "Generar" por un olvido trivial del usuario;
 * "no filtrar" es el comportamiento menos sorprendente y evita un reporte vacío por
 * accidente.
 */
export function filtrarPartidasParaReporte(
  actuales: Partida[],
  historicas: Partida[],
  filtros: FiltrosReportePendientes,
): Partida[] {
  const combinadas = filtros.incluirHistoricas ? [...actuales, ...historicas] : actuales;

  const filtraPorTipoMovimiento = filtros.debito || filtros.credito;
  const filtraPorTipoOrigen = filtros.bancario || filtros.contable;

  return combinadas
    .filter(p => !filtraPorTipoMovimiento
      || (filtros.debito && p.tipoMovimiento === 'DEBITO')
      || (filtros.credito && p.tipoMovimiento === 'CREDITO'))
    .filter(p => !filtraPorTipoOrigen
      || (filtros.bancario && p.tipoOrigen === 'BANCARIO')
      || (filtros.contable && p.tipoOrigen === 'CONTABLE'))
    .sort((a, b) => (a.fechaMovimiento ?? '').localeCompare(b.fechaMovimiento ?? ''));
}

/** dd/MM/yyyy a partir de una fecha ISO (yyyy-MM-dd) tal como la sirve el backend. */
function formatearFecha(fecha: string | null): string {
  if (!fecha) return '';
  const [anio, mes, dia] = fecha.split('-');
  if (!anio || !mes || !dia) return fecha;
  return `${dia}/${mes}/${anio}`;
}

/**
 * Moneda COP con signo según débito (-)/crédito (+), p. ej. "-$120.000" / "+$85.000".
 * Arma el string a mano (signo + "$" + separador de miles) en vez de usar
 * Intl.NumberFormat con style:'currency' -- ese estilo intercala un espacio entre el
 * símbolo y el número ("$ 120.000") que no calza con el formato pedido, y el símbolo/
 * espaciado de 'currency' varía entre versiones de ICU (Node vs. el Chrome de Karma).
 * El separador de miles vía toLocaleString sí es estable para es-CO.
 */
function formatearValor(monto: number | null, tipo: string | null): string {
  const valor = Math.round(Math.abs(monto ?? 0));
  const signo = tipo === 'DEBITO' ? '-' : '+';
  return `${signo}$${valor.toLocaleString('es-CO')}`;
}

/**
 * Construye las filas de la tabla del PDF (Fecha, Descripción, Valor, Origen) a partir de
 * las partidas ya filtradas. Extraída como función pura -- sin tocar jsPDF -- para poder
 * testear la lógica de negocio real (formato de fecha/moneda/origen) sin mockear la
 * generación del PDF.
 */
export function filasParaPdf(partidas: Partida[]): string[][] {
  return partidas.map(p => [
    formatearFecha(p.fechaMovimiento),
    p.descripcionMovimiento ?? '',
    formatearValor(p.montoMovimiento, p.tipoMovimiento),
    p.tipoOrigen === 'BANCARIO' ? 'Bancario' : 'Contable',
  ]);
}

/** "Bancolombia — Período conciliado: 2026-07". Lanza si falta banco o período. */
export function tituloReporte(conciliacion: Conciliacion): string {
  if (!conciliacion.nombreBanco || !conciliacion.periodo) {
    throw new Error('La conciliación no tiene banco o período cargado');
  }
  return `${conciliacion.nombreBanco} — Período conciliado: ${conciliacion.periodo}`;
}

/** Quita tildes/diacríticos y caracteres fuera de [a-z0-9-], espacios -> guiones. */
function normalizarParaArchivo(texto: string): string {
  return texto
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9-]/g, '');
}

/** "reporte-pendientes_bancolombia_2026-07.pdf". Lanza si falta banco o período. */
export function nombreArchivoReporte(conciliacion: Conciliacion): string {
  if (!conciliacion.nombreBanco || !conciliacion.periodo) {
    throw new Error('La conciliación no tiene banco o período cargado');
  }
  const banco = normalizarParaArchivo(conciliacion.nombreBanco);
  return `reporte-pendientes_${banco}_${conciliacion.periodo}.pdf`;
}
