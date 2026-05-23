import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { DashboardComponent } from './dashboard.component';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MetricasResumen } from '../../core/models';

const mockMetricas: MetricasResumen = {
  totalConciliaciones: 10,
  enBorrador: 3,
  enRevision: 2,
  cerradas: 5,
  totalMovimientosBancarios: 200,
  totalMovimientosContables: 195,
  totalSugerencias: 180,
  sugerenciasAceptadas: 150,
  sugerenciasRechazadas: 20,
  sugerenciasPendientes: 10,
  diferenciaPromedio: 500
};

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let apiSpy: jasmine.SpyObj<ApiService>;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('ApiService', ['obtenerMetricas']);
    authSpy = jasmine.createSpyObj('AuthService', ['hasRole', 'getEmail', 'getRol']);
    apiSpy.obtenerMetricas.and.returnValue(of({ success: true, data: mockMetricas, message: '', timestamp: '' }));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, NoopAnimationsModule, RouterTestingModule],
      providers: [
        { provide: ApiService, useValue: apiSpy },
        { provide: AuthService, useValue: authSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('llama a obtenerMetricas en ngOnInit', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    expect(apiSpy.obtenerMetricas).toHaveBeenCalledTimes(1);
  }));

  it('asigna las métricas y desactiva loading tras respuesta exitosa', fakeAsync(() => {
    fixture.detectChanges();
    tick();
    expect(component.metricas).toEqual(mockMetricas);
    expect(component.loading).toBeFalse();
  }));

  it('desactiva loading aunque falle la API', fakeAsync(() => {
    apiSpy.obtenerMetricas.and.returnValue(throwError(() => new Error('Error API')));
    fixture.detectChanges();
    tick();
    expect(component.loading).toBeFalse();
    expect(component.metricas).toBeNull();
  }));

  describe('canCreate()', () => {
    it('retorna true para CONTADOR', () => {
      authSpy.hasRole.and.returnValue(true);
      expect(component.canCreate()).toBeTrue();
    });

    it('retorna false para FINANZAS', () => {
      authSpy.hasRole.and.returnValue(false);
      expect(component.canCreate()).toBeFalse();
    });
  });
});
