import { Conciliacion, Partida } from '../../core/models';
import {
  FiltrosReportePendientes,
  filasParaPdf,
  filtrarPartidasParaReporte,
  nombreArchivoReporte,
  tituloReporte,
} from './reporte-pendientes.util';

function partida(overrides: Partial<Partida>): Partida {
  return {
    id: 1, idConciliacion: 1, idMovimiento: 1, tipoOrigen: 'BANCARIO', estado: 'PENDIENTE',
    justificacion: null, fechaJustificacion: null, periodoArrastre: null,
    fechaMovimiento: '2026-07-10', descripcionMovimiento: 'desc', montoMovimiento: 1000,
    tipoMovimiento: 'DEBITO', ultimosDigitosTarjeta: null, esHistorica: false,
    ...overrides,
  };
}

function conciliacion(overrides: Partial<Conciliacion>): Conciliacion {
  return {
    id: 1, periodo: '2026-07', idCuenta: 1, numeroCuenta: 'CC-1', tipoCuenta: 'CORRIENTE',
    idBanco: 1, nombreBanco: 'Banco Test', estado: 'BORRADOR', idUsuarioCreador: 1,
    idUsuarioAprobador: null, tsCreacion: '', tsCierre: null,
    saldoExtracto: null, saldoAuxiliar: null, diferenciaSaldo: null, auxiliarConjunto: false,
    ...overrides,
  };
}

const filtrosBase: FiltrosReportePendientes = {
  debito: true, credito: true, bancario: true, contable: true, incluirHistoricas: false,
};

describe('reporte-pendientes.util', () => {

  describe('filtrarPartidasParaReporte — combinación débito/crédito x bancario/contable', () => {
    const debBanc = partida({ id: 1, tipoMovimiento: 'DEBITO', tipoOrigen: 'BANCARIO' });
    const credBanc = partida({ id: 2, tipoMovimiento: 'CREDITO', tipoOrigen: 'BANCARIO' });
    const debCont = partida({ id: 3, tipoMovimiento: 'DEBITO', tipoOrigen: 'CONTABLE' });
    const credCont = partida({ id: 4, tipoMovimiento: 'CREDITO', tipoOrigen: 'CONTABLE' });
    const actuales = [debBanc, credBanc, debCont, credCont];

    it('todo marcado trae las 4 combinaciones', () => {
      const resultado = filtrarPartidasParaReporte(actuales, [], filtrosBase);
      expect(resultado.map(p => p.id).sort()).toEqual([1, 2, 3, 4]);
    });

    it('ningún checkbox de tipo de movimiento marcado se trata como ambos (no filtra esa dimensión)', () => {
      const filtros: FiltrosReportePendientes = { ...filtrosBase, debito: false, credito: false };
      const resultado = filtrarPartidasParaReporte(actuales, [], filtros);
      expect(resultado.map(p => p.id).sort()).toEqual([1, 2, 3, 4]);
    });

    it('ningún checkbox de origen marcado se trata como ambos (no filtra esa dimensión)', () => {
      const filtros: FiltrosReportePendientes = { ...filtrosBase, bancario: false, contable: false };
      const resultado = filtrarPartidasParaReporte(actuales, [], filtros);
      expect(resultado.map(p => p.id).sort()).toEqual([1, 2, 3, 4]);
    });

    it('solo débitos bancarios (combinación de ambas dimensiones a la vez)', () => {
      const filtros: FiltrosReportePendientes = { debito: true, credito: false, bancario: true, contable: false, incluirHistoricas: false };
      const resultado = filtrarPartidasParaReporte(actuales, [], filtros);
      expect(resultado).toEqual([debBanc]);
    });

    it('solo créditos contables', () => {
      const filtros: FiltrosReportePendientes = { debito: false, credito: true, bancario: false, contable: true, incluirHistoricas: false };
      const resultado = filtrarPartidasParaReporte(actuales, [], filtros);
      expect(resultado).toEqual([credCont]);
    });

    it('solo débitos, cualquier origen', () => {
      const filtros: FiltrosReportePendientes = { debito: true, credito: false, bancario: true, contable: true, incluirHistoricas: false };
      const resultado = filtrarPartidasParaReporte(actuales, [], filtros);
      expect(resultado.map(p => p.id).sort()).toEqual([1, 3]);
    });

    it('solo bancarios, cualquier tipo de movimiento', () => {
      const filtros: FiltrosReportePendientes = { debito: true, credito: true, bancario: true, contable: false, incluirHistoricas: false };
      const resultado = filtrarPartidasParaReporte(actuales, [], filtros);
      expect(resultado.map(p => p.id).sort()).toEqual([1, 2]);
    });
  });

  describe('filtrarPartidasParaReporte — inclusión/exclusión de históricas', () => {
    const actual = partida({ id: 1, fechaMovimiento: '2026-07-15' });
    const historica = partida({ id: 99, fechaMovimiento: '2026-05-01', esHistorica: true });

    it('con incluirHistoricas=false, no fusiona las históricas', () => {
      const resultado = filtrarPartidasParaReporte([actual], [historica], { ...filtrosBase, incluirHistoricas: false });
      expect(resultado.map(p => p.id)).toEqual([1]);
    });

    it('con incluirHistoricas=true, fusiona actuales + históricas ordenadas por fecha', () => {
      const resultado = filtrarPartidasParaReporte([actual], [historica], { ...filtrosBase, incluirHistoricas: true });
      expect(resultado.map(p => p.id)).toEqual([99, 1]); // histórica (mayo) antes que actual (julio)
    });

    it('los filtros de débito/crédito y bancario/contable se aplican también sobre las históricas incluidas', () => {
      const historicaCredito = partida({ id: 98, fechaMovimiento: '2026-04-01', tipoMovimiento: 'CREDITO', esHistorica: true });
      const filtros: FiltrosReportePendientes = { debito: true, credito: false, bancario: true, contable: true, incluirHistoricas: true };
      const resultado = filtrarPartidasParaReporte([actual], [historica, historicaCredito], filtros);
      expect(resultado.map(p => p.id)).toEqual([99, 1]); // historicaCredito (CREDITO) queda fuera
    });
  });

  describe('tituloReporte', () => {
    it('arma "Banco — Período conciliado: periodo"', () => {
      const conc = conciliacion({ nombreBanco: 'Bancolombia', periodo: '2026-07' });
      expect(tituloReporte(conc)).toBe('Bancolombia — Período conciliado: 2026-07');
    });

    it('lanza si falta el banco', () => {
      const conc = conciliacion({ nombreBanco: null, periodo: '2026-07' });
      expect(() => tituloReporte(conc)).toThrow();
    });

    it('lanza si falta el período', () => {
      const conc = conciliacion({ nombreBanco: 'Bancolombia', periodo: '' });
      expect(() => tituloReporte(conc)).toThrow();
    });
  });

  describe('nombreArchivoReporte', () => {
    it('sigue el patrón reporte-pendientes_{banco}_{periodo}.pdf del enunciado', () => {
      const conc = conciliacion({ nombreBanco: 'Bancolombia', periodo: '2026-07' });
      expect(nombreArchivoReporte(conc)).toBe('reporte-pendientes_bancolombia_2026-07.pdf');
    });

    it('normaliza espacios y tildes del nombre del banco', () => {
      const conc = conciliacion({ nombreBanco: 'Banco de Bogotá', periodo: '2026-07' });
      expect(nombreArchivoReporte(conc)).toBe('reporte-pendientes_banco-de-bogota_2026-07.pdf');
    });

    it('lanza si falta el banco o el período', () => {
      const conc = conciliacion({ nombreBanco: null, periodo: '2026-07' });
      expect(() => nombreArchivoReporte(conc)).toThrow();
    });
  });

  describe('filasParaPdf', () => {
    it('formatea fecha dd/MM/yyyy, valor con signo y origen legible', () => {
      const debito = partida({
        fechaMovimiento: '2026-07-10', descripcionMovimiento: 'Pago proveedor',
        montoMovimiento: 120000, tipoMovimiento: 'DEBITO', tipoOrigen: 'BANCARIO',
      });
      const credito = partida({
        fechaMovimiento: '2026-07-11', descripcionMovimiento: 'Consignación',
        montoMovimiento: 85000, tipoMovimiento: 'CREDITO', tipoOrigen: 'CONTABLE',
      });

      const filas = filasParaPdf([debito, credito]);

      expect(filas[0]).toEqual(['10/07/2026', 'Pago proveedor', '-$120.000', 'Bancario']);
      expect(filas[1]).toEqual(['11/07/2026', 'Consignación', '+$85.000', 'Contable']);
    });

    it('descripción null se muestra como celda vacía, no "null"', () => {
      const sinDescripcion = partida({ descripcionMovimiento: null });
      const filas = filasParaPdf([sinDescripcion]);
      expect(filas[0][1]).toBe('');
    });
  });
});
