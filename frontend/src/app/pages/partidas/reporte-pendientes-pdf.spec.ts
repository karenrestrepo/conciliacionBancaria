import { generarPdfPendientes } from './reporte-pendientes-pdf';
import { Conciliacion } from '../../core/models';

function conciliacion(overrides: Partial<Conciliacion> = {}): Conciliacion {
  return {
    id: 1, periodo: '2026-07', idCuenta: 1, numeroCuenta: 'CC-1', tipoCuenta: 'CORRIENTE',
    idBanco: 1, nombreBanco: 'Bancolombia', estado: 'BORRADOR', idUsuarioCreador: 1,
    idUsuarioAprobador: null, tsCreacion: '', tsCierre: null,
    saldoExtracto: null, saldoAuxiliar: null, diferenciaSaldo: null, auxiliarConjunto: false,
    ...overrides,
  };
}

// No se testea el contenido binario del PDF ni se mockea jsPDF/autoTable -- ver decisión en
// reporte-pendientes.util.spec.ts, donde vive y se testea la lógica de negocio real
// (filasParaPdf/tituloReporte/nombreArchivoReporte). jsPDF v4 no expone save()/text() como
// métodos de jsPDF.prototype -- son propiedades asignadas por instancia dentro del propio
// constructor (arquitectura interna de la librería) -- así que spyOn(jsPDF.prototype, ...)
// no es viable aquí; es exactamente el caso que el enunciado contempla como alternativa
// válida a mockear jsPDF/autoTable.
//
// Lo que SÍ se verifica: que generarPdfPendientes valida banco/período ANTES de instanciar
// jsPDF (falla rápido, sin generar nada) -- ese es el único camino sin efectos secundarios
// de descarga que se puede probar de forma determinista en Karma/ChromeHeadless.
describe('generarPdfPendientes', () => {
  it('lanza y no llega a crear el documento si falta el banco o el período de la conciliación', () => {
    expect(() => generarPdfPendientes(conciliacion({ nombreBanco: null }), [])).toThrowError(
      'La conciliación no tiene banco o período cargado');
    expect(() => generarPdfPendientes(conciliacion({ periodo: '' }), [])).toThrowError(
      'La conciliación no tiene banco o período cargado');
  });
});
