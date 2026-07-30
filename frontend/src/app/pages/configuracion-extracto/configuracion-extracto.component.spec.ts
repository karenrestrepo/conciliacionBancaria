import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ConfiguracionExtractoComponent } from './configuracion-extracto.component';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

describe('ConfiguracionExtractoComponent', () => {
  let component: ConfiguracionExtractoComponent;
  let fixture: ComponentFixture<ConfiguracionExtractoComponent>;
  let apiSpy: jasmine.SpyObj<ApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService', [
      'listarBancos', 'listarCuentasPorBanco', 'listarConfiguracionesPorBanco',
      'crearConfiguracion', 'actualizarConfiguracion', 'eliminarConfiguracion', 'probarConfiguracion'
    ]);
    apiSpy.listarBancos.and.returnValue(of({ success: true, message: '', data: [], timestamp: '' }));
    authSpy = jasmine.createSpyObj('AuthService', ['hasRole', 'getToken']);
    authSpy.hasRole.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [ConfiguracionExtractoComponent, ReactiveFormsModule, FormsModule, NoopAnimationsModule],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: AuthService, useValue: authSpy },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfiguracionExtractoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  describe('modo ancho fijo — columnas por posición sin regex', () => {
    beforeEach(() => {
      component.tipoArchivo = 'TXT';
      component.detalleForm.patchValue({ tipoOrigen: 'ANCHO_FIJO' });
    });

    it('agrega y quita columnas dinámicamente', () => {
      expect(component.anchoFijoColumnas.length).toBe(0);
      component.agregarColumna('dia', 1, 2);
      component.agregarColumna('mes', 4, 5);
      expect(component.anchoFijoColumnas.length).toBe(2);
      component.quitarColumna(0);
      expect(component.anchoFijoColumnas.length).toBe(1);
      expect(component.anchoFijoColumnas.at(0).get('campo')?.value).toBe('mes');
    });

    it('previewColumna extrae el texto correcto de la línea de ejemplo pegada', () => {
      component.agregarColumna('descripcion', 8, 20);
      component.lineaMuestra = '01   06  Abono ACH BANCOLOMBIA    800,670.00+';
      const preview = component.previewColumna(component.anchoFijoColumnas.at(0));
      // Posiciones 8-20 (1-based, inclusive) => substring(7,20) => " Abono ACH B" trimmed
      expect(preview).toBe('Abono ACH B');
    });

    it('previewColumna retorna vacío si la línea de ejemplo aún no se ha pegado', () => {
      component.agregarColumna('descripcion', 8, 20);
      component.lineaMuestra = '';
      expect(component.previewColumna(component.anchoFijoColumnas.at(0))).toBe('');
    });

    it('previewColumna no revienta con posiciones fuera de rango', () => {
      component.agregarColumna('descripcion', 100, 200);
      component.lineaMuestra = 'linea corta';
      expect(component.previewColumna(component.anchoFijoColumnas.at(0))).toBe('');
    });

    it('construye el ConfiguracionExtractoDetalle con tipoOrigen ANCHO_FIJO y las columnas configuradas', () => {
      component.agregarColumna('dia', 1, 2);
      component.agregarColumna('mes', 4, 5);
      component.agregarColumna('monto', 6, 20);
      component.detalleForm.patchValue({ convencionSigno: 'SUFIJO' });

      const detalle = (component as any).construirDetalle();

      expect(detalle.tipoOrigen).toBe('ANCHO_FIJO');
      expect(detalle.anchoFijo.columnas).toEqual([
        { campo: 'dia', inicio: 1, fin: 2 },
        { campo: 'mes', inicio: 4, fin: 5 },
        { campo: 'monto', inicio: 6, fin: 20 }
      ]);
      expect(detalle.anchoFijo.convencionSigno).toBe('SUFIJO');
      expect(detalle.delimitado).toBeUndefined();
      expect(detalle.excel).toBeUndefined();
    });

    it('incluye invertir=true (tarjeta de credito) en el detalle construido', () => {
      component.agregarColumna('fecha', 1, 8);
      component.agregarColumna('monto', 10, 20);
      component.detalleForm.patchValue({ convencionSigno: 'SUFIJO', anchoFijoInvertir: true });

      const detalle = (component as any).construirDetalle();

      expect(detalle.anchoFijo.invertir).toBeTrue();
    });
  });

  describe('detección automática y validación en vivo del formato de fecha', () => {
    it('detecta yyyyMMdd para texto de 8 digitos con anio plausible al inicio (caso real reportado)', () => {
      expect(component.detectarFormatoFecha('20260428')).toBe('yyyyMMdd');
    });

    it('detecta ddMMyyyy para texto de 8 digitos donde el anio plausible queda al final', () => {
      expect(component.detectarFormatoFecha('28042026')).toBe('ddMMyyyy');
    });

    it('detecta formato con separador "/" y anio al inicio', () => {
      expect(component.detectarFormatoFecha('2026/04/28')).toBe('yyyy/MM/dd');
    });

    it('detecta formato con separador "-" y anio al final', () => {
      expect(component.detectarFormatoFecha('28-04-2026')).toBe('dd-MM-yyyy');
    });

    it('texto vacio o irreconocible no produce formato', () => {
      expect(component.detectarFormatoFecha('')).toBeNull();
      expect(component.detectarFormatoFecha('abc')).toBeNull();
    });

    it('al pegar la linea de ejemplo, autodetecta y sobreescribe el formato aunque el usuario haya puesto uno con separadores por error', () => {
      component.tipoArchivo = 'TXT';
      component.detalleForm.patchValue({ tipoOrigen: 'ANCHO_FIJO' });
      component.agregarColumna('fecha', 18, 25);
      // El usuario, engañado por el placeholder "dd/MM/yyyy", escribe un formato con barras...
      component.detalleForm.patchValue({ anchoFijoFormatoFecha: 'yyyy/MM/dd' });

      // ...pero al pegar una línea real sin separadores, se autodetecta el formato correcto.
      component.lineaMuestra = '        1017646  20260504  IMP 4XMIL';
      component.onLineaMuestraChange();

      expect(component.detalleForm.get('anchoFijoFormatoFecha')?.value).toBe('yyyyMMdd');
    });

    it('feedbackFormatoFecha reporta error cuando el formato tiene separadores pero el texto no', () => {
      component.tipoArchivo = 'TXT';
      component.detalleForm.patchValue({ tipoOrigen: 'ANCHO_FIJO' });
      component.agregarColumna('fecha', 18, 25);
      component.detalleForm.patchValue({ anchoFijoFormatoFecha: 'yyyy/MM/dd' }, { emitEvent: false });
      component.lineaMuestra = '        1017646  20260504  IMP 4XMIL';

      const feedback = component.feedbackFormatoFecha();

      expect(feedback?.ok).toBeFalse();
    });

    it('feedbackFormatoFecha reporta exito y la fecha interpretada cuando el formato coincide', () => {
      component.tipoArchivo = 'TXT';
      component.detalleForm.patchValue({ tipoOrigen: 'ANCHO_FIJO' });
      component.agregarColumna('fecha', 18, 25);
      component.detalleForm.patchValue({ anchoFijoFormatoFecha: 'yyyyMMdd' }, { emitEvent: false });
      component.lineaMuestra = '        1017646  20260504  IMP 4XMIL';

      const feedback = component.feedbackFormatoFecha();

      expect(feedback?.ok).toBeTrue();
      expect(feedback?.mensaje).toContain('2026');
    });
  });

  describe('cuadre', () => {
    it('incluye las etiquetas de cuadre cuando está habilitado', () => {
      component.tipoArchivo = 'CSV';
      component.detalleForm.patchValue({
        tipoOrigen: 'DELIMITADO',
        cuadreHabilitada: true,
        cuadreEtiquetaSaldoAnterior: 'Saldo Anterior',
        cuadreEtiquetaCreditos: 'Creditos',
        cuadreEtiquetaDebitos: 'Debitos',
        cuadreEtiquetaSaldoFinal: 'Saldo Final'
      });

      const detalle = (component as any).construirDetalle();

      expect(detalle.cuadre.habilitada).toBeTrue();
      expect(detalle.cuadre.etiquetaSaldoAnterior).toBe('Saldo Anterior');
    });
  });

  describe('editar() — round-trip de una configuración guardada', () => {
    it('repuebla el formulario de ancho fijo desde el JSON guardado', () => {
      const configGuardada = {
        id: 1, idBanco: 5, nombreBanco: 'Davivienda', nombre: 'Cta ahorro',
        tipoArchivo: 'TXT' as const, aplicaParaTodasLasCuentas: true, idsCuentas: [],
        configuracionDetalle: JSON.stringify({
          tipoOrigen: 'ANCHO_FIJO',
          encoding: 'ISO-8859-1',
          separadorMiles: ',', separadorDecimales: '.',
          anchoFijo: { columnas: [{ campo: 'dia', inicio: 15, fin: 16 }], convencionSigno: 'SUFIJO' },
          continuacion: { habilitada: false },
          cuadre: { habilitada: true, etiquetaSaldoAnterior: 'Saldo Anterior' }
        }),
        activo: true, fechaCreacion: '', fechaModificacion: null
      };

      component.editar(configGuardada);

      expect(component.editandoId).toBe(1);
      expect(component.tipoArchivo).toBe('TXT');
      expect(component.detalleForm.get('tipoOrigen')?.value).toBe('ANCHO_FIJO');
      expect(component.anchoFijoColumnas.length).toBe(1);
      expect(component.anchoFijoColumnas.at(0).get('campo')?.value).toBe('dia');
      expect(component.detalleForm.get('cuadreHabilitada')?.value).toBeTrue();
      expect(component.detalleForm.get('cuadreEtiquetaSaldoAnterior')?.value).toBe('Saldo Anterior');
    });
  });

  describe('probar configuración con archivo de muestra', () => {
    it('llama a la API con el detalle construido y expone el resultado', () => {
      component.tipoArchivo = 'CSV';
      component.detalleForm.patchValue({ tipoOrigen: 'DELIMITADO' });
      const resultadoMock = {
        movimientos: [{ fecha: '2026-06-01', descripcion: 'x', monto: 100, tipo: 'DEBITO' }],
        totalMovimientos: 1,
        cuadre: { habilitada: false, cuadra: true, advertencias: [] },
        advertencias: []
      };
      apiSpy.probarConfiguracion.and.returnValue(of({ success: true, message: '', data: resultadoMock, timestamp: '' }));

      const archivo = new File(['contenido'], 'muestra.csv');
      const event = { target: { files: [archivo], value: '' } } as unknown as Event;
      component.onArchivoMuestraSeleccionado(event);

      expect(apiSpy.probarConfiguracion).toHaveBeenCalled();
      expect(component.resultadoPrueba).toEqual(resultadoMock);
      expect(component.probando).toBeFalse();
    });
  });
});
