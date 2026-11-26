import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { SugerenciasComponent } from './sugerencias.component';

describe('SugerenciasComponent — recarga del auxiliar', () => {
  let component: SugerenciasComponent;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(() => {
    const api = jasmine.createSpyObj<ApiService>('ApiService', ['obtenerConciliacion']);
    api.obtenerConciliacion.and.returnValue(of({ data: null } as any));
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    TestBed.configureTestingModule({
      providers: [
        { provide: ApiService, useValue: api },
        { provide: AuthService, useValue: {} },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } }
      ]
    });
    component = TestBed.runInInjectionContext(() => new SugerenciasComponent(
      TestBed.inject(ApiService), TestBed.inject(AuthService),
      TestBed.inject(ActivatedRoute), TestBed.inject(MatSnackBar)));
    spyOn(component, 'cargarSugerencias');
  });

  it('ocultar el panel lo conserva para no cancelar una lectura', () => {
    component.alternarPanelAuxiliar();
    component.alternarPanelAuxiliar();

    expect(component.auxiliarAbierto).toBeTrue();
    expect(component.auxiliarVisible).toBeFalse();
  });

  it('al cargar el auxiliar cierra el panel, informa y refresca', () => {
    component.alternarPanelAuxiliar();

    component.onAuxiliarCargado({ jobId: null, nuevos: 0, anulados: 2, revertidos: 1 });

    expect(component.auxiliarAbierto).toBeFalse();
    expect(snackBar.open.calls.mostRecent().args[0]).toContain('2 anulado(s)');
    expect(component.cargarSugerencias).toHaveBeenCalled();
  });
});
