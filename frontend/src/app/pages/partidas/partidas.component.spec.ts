import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { PartidasComponent } from './partidas.component';
import { ApiService } from '../../core/api.service';
import { Conciliacion } from '../../core/models';

describe('PartidasComponent', () => {
  let component: PartidasComponent;
  let fixture: ComponentFixture<PartidasComponent>;
  let apiSpy: jasmine.SpyObj<ApiService>;

  const conciliacionRespuesta = (periodo: string): { success: true; message: string; timestamp: string; data: Conciliacion } => ({
    success: true, message: '', timestamp: '',
    data: { id: 1, periodo, idCuenta: 1, numeroCuenta: 'CC-001', tipoCuenta: 'CORRIENTE',
      idBanco: 1, nombreBanco: 'Banco Test', estado: 'BORRADOR', idUsuarioCreador: 1,
      idUsuarioAprobador: null, tsCreacion: '', tsCierre: null,
      saldoExtracto: null, saldoAuxiliar: null, diferenciaSaldo: null, auxiliarConjunto: false }
  });

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService', [
      'obtenerConciliacion', 'listarPartidas', 'listarPartidasHistoricas',
      'listarGastosAgrupadosConciliacion', 'arrastrarPartida', 'cruzarPartidas'
    ]);
    apiSpy.obtenerConciliacion.and.returnValue(of(conciliacionRespuesta('2026-07')));
    apiSpy.listarPartidas.and.returnValue(of({ success: true, message: '', timestamp: '', data: [] }));
    apiSpy.listarPartidasHistoricas.and.returnValue(of({ success: true, message: '', timestamp: '', data: [] }));
    apiSpy.listarGastosAgrupadosConciliacion.and.returnValue(of({ success: true, message: '', timestamp: '', data: [] }));

    await TestBed.configureTestingModule({
      imports: [PartidasComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: MatSnackBar, useValue: jasmine.createSpyObj('MatSnackBar', ['open']) },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PartidasComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  describe('siguienteMes()', () => {
    it('calcula el mes siguiente al período de la conciliación, no al de hoy', fakeAsync(() => {
      apiSpy.obtenerConciliacion.and.returnValue(of(conciliacionRespuesta('2025-11')));
      fixture.detectChanges();
      tick();

      expect(apiSpy.obtenerConciliacion).toHaveBeenCalledWith(1);
      expect(component.siguienteMes()).toBe('2025-12');
    }));

    it('pasa de diciembre a enero del año siguiente', fakeAsync(() => {
      apiSpy.obtenerConciliacion.and.returnValue(of(conciliacionRespuesta('2025-12')));
      fixture.detectChanges();
      tick();

      expect(component.siguienteMes()).toBe('2026-01');
    }));

    it('cae al fallback de la fecha de hoy si no se pudo cargar el período', fakeAsync(() => {
      component.periodoConciliacion = null;

      const hoy = new Date();
      hoy.setMonth(hoy.getMonth() + 1);
      const esperado = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`;

      expect(component.siguienteMes()).toBe(esperado);
    }));
  });

  describe('carga inicial', () => {
    it('pide el período de la conciliación antes de listar las partidas', fakeAsync(() => {
      fixture.detectChanges();
      tick();

      expect(apiSpy.obtenerConciliacion).toHaveBeenCalledWith(1);
      expect(component.periodoConciliacion).toBe('2026-07');
      expect(apiSpy.listarPartidas).toHaveBeenCalledWith(1);
    }));

    it('sigue cargando las partidas aunque falle la carga del período', fakeAsync(() => {
      apiSpy.obtenerConciliacion.and.returnValue(throwError(() => new Error('fallo')));
      fixture.detectChanges();
      tick();

      expect(apiSpy.listarPartidas).toHaveBeenCalledWith(1);
      expect(component.periodoConciliacion).toBeNull();
    }));
  });

  describe('actualizarLocal (a través de confirmarCruce)', () => {
    it('agrega una partida nueva a la lista cuando el backend la crea (p.ej. el resto de un cruce)', () => {
      // cruzarPartidas() devuelve un observable síncrono (of(...)) -- no hace falta
      // fakeAsync/tick, y evita un temporizador interno de Angular Material que Karma
      // reporta como "pendiente" sin relación con la lógica bajo prueba.
      component.partidas = [{ id: 1, estado: 'PENDIENTE' }];
      apiSpy.cruzarPartidas.and.returnValue(of({
        success: true, message: '', timestamp: '',
        data: [
          { id: 1, estado: 'CRUZADA', justificacion: 'Cruzado con diferencia trasladada a partida #500' },
          { id: 500, estado: 'PENDIENTE', tipoOrigen: 'BANCARIO', montoMovimiento: 223 }
        ]
      }));

      component.cruzarSeleccion.add(2);
      component.confirmarCruce({ id: 1, montoMovimiento: 723, tipoMovimiento: 'DEBITO' });

      expect(component.partidas.find(p => p.id === 500)).toBeTruthy();
      expect(component.partidas.find(p => p.id === 1)?.estado).toBe('CRUZADA');
    });
  });
});
