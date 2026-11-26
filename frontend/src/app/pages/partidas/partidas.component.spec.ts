import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import { PartidasComponent } from './partidas.component';
import { ApiService } from '../../core/api.service';
import { Conciliacion, Partida } from '../../core/models';
import { ReportePendientesDialogComponent } from './reporte-pendientes-dialog.component';
import { FiltrosReportePendientes } from './reporte-pendientes.util';

describe('PartidasComponent', () => {
  let component: PartidasComponent;
  let fixture: ComponentFixture<PartidasComponent>;
  let apiSpy: jasmine.SpyObj<ApiService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;
  let snackBarSpy: jasmine.SpyObj<MatSnackBar>;

  const conciliacionRespuesta = (periodo: string, nombreBanco: string | null = 'Banco Test'):
      { success: true; message: string; timestamp: string; data: Conciliacion } => ({
    success: true, message: '', timestamp: '',
    data: { id: 1, periodo, idCuenta: 1, numeroCuenta: 'CC-001', tipoCuenta: 'CORRIENTE',
      idBanco: 1, nombreBanco, estado: 'BORRADOR', idUsuarioCreador: 1,
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

    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);
    snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [PartidasComponent, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } }
      ]
    }).compileComponents();

    // overrideProvider (no basta con "providers" arriba) -- PartidasComponent importa
    // MatDialogModule/MatSnackBarModule directamente en su propio arreglo "imports"
    // (standalone), y eso sombrea el override de un provider "providedIn: root" puesto solo
    // en el nivel de TestBed.configureTestingModule -- el componente termina resolviendo el
    // MatDialog/MatSnackBar reales en vez del spy.
    TestBed.overrideProvider(MatDialog, { useValue: dialogSpy });
    TestBed.overrideProvider(MatSnackBar, { useValue: snackBarSpy });

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

  describe('reporte de pendientes', () => {
    const filtrosTodos: FiltrosReportePendientes = {
      debito: true, credito: true, bancario: true, contable: true, incluirHistoricas: false,
    };

    beforeEach(() => {
      // Estos tests llaman a abrirReporte()/generarReporte() directamente, sin pasar por
      // ngOnInit() (fixture.detectChanges()) -- así que idConciliacion, que ngOnInit arma
      // desde la ruta, hay que fijarlo a mano igual que en el resto de este describe.
      component.idConciliacion = 1;
    });

    it('abrirReporte muestra un error y no abre el diálogo si falta el banco de la conciliación', () => {
      component.conciliacion = conciliacionRespuesta('2026-07', null).data;

      component.abrirReporte();

      expect(dialogSpy.open).not.toHaveBeenCalled();
      expect(snackBarSpy.open).toHaveBeenCalled();
    });

    it('abrirReporte abre el diálogo de filtros cuando banco y período están cargados', () => {
      component.conciliacion = conciliacionRespuesta('2026-07').data;
      dialogSpy.open.and.returnValue({ afterClosed: () => of(undefined) } as any);

      component.abrirReporte();

      expect(dialogSpy.open).toHaveBeenCalledWith(ReportePendientesDialogComponent, jasmine.any(Object));
    });

    // jsPDF v4 no expone save() en su prototype (ver decisión en reporte-pendientes-pdf.spec.ts)
    // así que estos tests no interceptan la descarga real; verifican en su lugar lo que sí es
    // observable desde fuera: si se pidieron o no las históricas, si se mostró un error, y que
    // generandoReporte vuelve a false. La construcción del arreglo filtrado y el armado del PDF
    // en sí ya están cubiertos por reporte-pendientes.util.spec.ts.
    it('generarReporte no pide históricas si incluirHistoricas=false, y no muestra error', () => {
      component.conciliacion = conciliacionRespuesta('2026-07').data;

      component.generarReporte(filtrosTodos);

      expect(apiSpy.listarPartidasHistoricas).not.toHaveBeenCalled();
      expect(snackBarSpy.open).not.toHaveBeenCalled();
      expect(component.generandoReporte).toBeFalse();
    });

    it('generarReporte pide históricas frescas al backend cuando incluirHistoricas=true', () => {
      component.conciliacion = conciliacionRespuesta('2026-07').data;
      component.partidas = [
        { id: 1, estado: 'PENDIENTE', tipoOrigen: 'BANCARIO', tipoMovimiento: 'DEBITO',
          fechaMovimiento: '2026-07-05', montoMovimiento: 100 },
      ];
      const historica: Partial<Partida> = {
        id: 2, tipoOrigen: 'CONTABLE', tipoMovimiento: 'CREDITO',
        fechaMovimiento: '2026-05-01', montoMovimiento: 50,
      };
      apiSpy.listarPartidasHistoricas.and.returnValue(of({ success: true, message: '', timestamp: '', data: [historica] }));

      component.generarReporte({ ...filtrosTodos, incluirHistoricas: true });

      expect(apiSpy.listarPartidasHistoricas).toHaveBeenCalledWith(1);
      expect(snackBarSpy.open).not.toHaveBeenCalled();
      expect(component.generandoReporte).toBeFalse();
    });

    it('muestra un error si falla la carga de históricas y no genera el PDF', () => {
      component.conciliacion = conciliacionRespuesta('2026-07').data;
      apiSpy.listarPartidasHistoricas.and.returnValue(throwError(() => new Error('fallo')));

      component.generarReporte({ ...filtrosTodos, incluirHistoricas: true });

      expect(snackBarSpy.open).toHaveBeenCalled();
      expect(component.generandoReporte).toBeFalse();
    });

    it('muestra un error (vía catch) si falta el banco al momento de generar, sin lanzar fuera del componente', () => {
      component.conciliacion = conciliacionRespuesta('2026-07', null).data;

      expect(() => component.generarReporte(filtrosTodos)).not.toThrow();

      expect(snackBarSpy.open).toHaveBeenCalled();
      expect(component.generandoReporte).toBeFalse();
    });
  });
});
