import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { ConciliacionesComponent } from './conciliaciones.component';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Conciliacion } from '../../core/models';

const mockConciliaciones: Conciliacion[] = [
  { id: 1, periodo: '2025-01', estado: 'BORRADOR', idUsuarioCreador: 1, idUsuarioAprobador: null, tsCreacion: '2025-01-01T00:00:00', tsCierre: null, saldoExtracto: null, saldoAuxiliar: null, diferenciaSaldo: null },
  { id: 2, periodo: '2025-02', estado: 'CERRADA', idUsuarioCreador: 1, idUsuarioAprobador: 2, tsCreacion: '2025-02-01T00:00:00', tsCierre: '2025-02-28T00:00:00', saldoExtracto: 10000, saldoAuxiliar: 10000, diferenciaSaldo: 0 }
];

describe('ConciliacionesComponent', () => {
  let component: ConciliacionesComponent;
  let fixture: ComponentFixture<ConciliacionesComponent>;
  let apiSpy: jasmine.SpyObj<ApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService', ['listarConciliaciones']);
    authSpy = jasmine.createSpyObj('AuthService', ['hasRole']);
    apiSpy.listarConciliaciones.and.returnValue(of({ success: true, data: mockConciliaciones, message: '', timestamp: '' }));

    await TestBed.configureTestingModule({
      imports: [ConciliacionesComponent, NoopAnimationsModule, RouterTestingModule],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: AuthService, useValue: authSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConciliacionesComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('llama a listarConciliaciones en ngOnInit', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    expect(apiSpy.listarConciliaciones).toHaveBeenCalledTimes(1);
  }));

  it('carga la lista y desactiva loading', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    expect(component.conciliaciones).toEqual(mockConciliaciones);
    expect(component.loading).toBeFalse();
  }));

  it('desactiva loading aunque falle la API', fakeAsync(() => {
    apiSpy.listarConciliaciones.and.returnValue(throwError(() => new Error('Error')));
    fixture.detectChanges();
    tick();
    expect(component.loading).toBeFalse();
  }));

  describe('getEstadoClass()', () => {
    it('retorna clase correcta para BORRADOR', () => {
      expect(component.getEstadoClass('BORRADOR')).toBe('estado-borrador');
    });
    it('retorna clase correcta para EN_REVISION', () => {
      expect(component.getEstadoClass('EN_REVISION')).toBe('estado-revision');
    });
    it('retorna clase correcta para CERRADA', () => {
      expect(component.getEstadoClass('CERRADA')).toBe('estado-cerrada');
    });
    it('retorna cadena vacía para estado desconocido', () => {
      expect(component.getEstadoClass('OTRO')).toBe('');
    });
  });

  describe('getEstadoLabel()', () => {
    it('retorna "Borrador" para BORRADOR', () => {
      expect(component.getEstadoLabel('BORRADOR')).toBe('Borrador');
    });
    it('retorna "En Revisión" para EN_REVISION', () => {
      expect(component.getEstadoLabel('EN_REVISION')).toBe('En Revisión');
    });
    it('retorna "Cerrada" para CERRADA', () => {
      expect(component.getEstadoLabel('CERRADA')).toBe('Cerrada');
    });
  });

  describe('getDiferenciaClass()', () => {
    it('retorna "diff-cero" para diferencia 0', () => {
      expect(component.getDiferenciaClass(0)).toBe('diff-cero');
    });
    it('retorna "diff-positivo" para diferencia positiva', () => {
      expect(component.getDiferenciaClass(500)).toBe('diff-positivo');
    });
    it('retorna "diff-negativo" para diferencia negativa', () => {
      expect(component.getDiferenciaClass(-200)).toBe('diff-negativo');
    });
    it('retorna cadena vacía para null', () => {
      expect(component.getDiferenciaClass(null)).toBe('');
    });
  });

  describe('canCreate()', () => {
    it('retorna true cuando el rol tiene permiso', () => {
      authSpy.hasRole.and.returnValue(true);
      expect(component.canCreate()).toBeTrue();
    });
    it('retorna false cuando el rol no tiene permiso', () => {
      authSpy.hasRole.and.returnValue(false);
      expect(component.canCreate()).toBeFalse();
    });
  });
});
