import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef } from '@angular/material/dialog';
import { MatCheckboxChange } from '@angular/material/checkbox';
import { DEFAULT_INCLUIR_HISTORICAS, ReportePendientesDialogComponent } from './reporte-pendientes-dialog.component';

function checkboxChange(checked: boolean): MatCheckboxChange {
  return { checked } as MatCheckboxChange;
}

describe('ReportePendientesDialogComponent', () => {
  let component: ReportePendientesDialogComponent;
  let fixture: ComponentFixture<ReportePendientesDialogComponent>;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ReportePendientesDialogComponent>>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [ReportePendientesDialogComponent, NoopAnimationsModule],
      providers: [{ provide: MatDialogRef, useValue: dialogRefSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportePendientesDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('arranca con las 4 casillas de detalle marcadas y "Todos" reflejándolas', () => {
    expect(component.debito).toBeTrue();
    expect(component.credito).toBeTrue();
    expect(component.bancario).toBeTrue();
    expect(component.contable).toBeTrue();
    expect(component.todos).toBeTrue();
  });

  it('"Incluir pendientes de meses anteriores" arranca en el valor de la constante default', () => {
    expect(component.incluirHistoricas).toBe(DEFAULT_INCLUIR_HISTORICAS);
  });

  it('"Todos" marca las 4 casillas de detalle', () => {
    component.onToggleDetalle('debito', checkboxChange(false));
    expect(component.todos).toBeFalse();

    component.onToggleTodos(checkboxChange(true));

    expect(component.debito).toBeTrue();
    expect(component.credito).toBeTrue();
    expect(component.bancario).toBeTrue();
    expect(component.contable).toBeTrue();
  });

  it('"Todos" desmarca las 4 casillas de detalle', () => {
    component.onToggleTodos(checkboxChange(false));

    expect(component.debito).toBeFalse();
    expect(component.credito).toBeFalse();
    expect(component.bancario).toBeFalse();
    expect(component.contable).toBeFalse();
  });

  it('desmarcar una casilla individual desmarca "Todos" automáticamente', () => {
    expect(component.todos).toBeTrue();

    component.onToggleDetalle('credito', checkboxChange(false));

    expect(component.todos).toBeFalse();
    expect(component.credito).toBeFalse();
  });

  it('volver a marcar las 4 casillas individuales marca "Todos" de nuevo', () => {
    component.onToggleDetalle('bancario', checkboxChange(false));
    expect(component.todos).toBeFalse();

    component.onToggleDetalle('bancario', checkboxChange(true));

    expect(component.todos).toBeTrue();
  });

  it('cancelar cierra el diálogo sin devolver filtros', () => {
    component.cancelar();
    expect(dialogRefSpy.close).toHaveBeenCalledWith();
  });

  it('generar cierra el diálogo devolviendo los filtros elegidos', () => {
    component.onToggleDetalle('credito', checkboxChange(false));
    component.onToggleDetalle('contable', checkboxChange(false));
    component.incluirHistoricas = true;

    component.generar();

    expect(dialogRefSpy.close).toHaveBeenCalledWith({
      debito: true, credito: false, bancario: true, contable: false, incluirHistoricas: true,
    });
  });
});
