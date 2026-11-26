import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ResultadoLectura } from '../../core/models';
import { LecturaIaComponent } from './lectura-ia.component';

describe('LecturaIaComponent', () => {
  let fixture: ComponentFixture<LecturaIaComponent>;
  let component: LecturaIaComponent;
  let api: jasmine.SpyObj<ApiService>;
  let emitidos: string[];

  const resultado = (estado: ResultadoLectura['estado'], mensaje: string): ResultadoLectura => ({
    estado,
    mensaje,
    jobId: estado === 'CARGADO' ? 'job-1' : null,
    revision: {
      lectura: {
        tipoProducto: 'CUENTA', ultimosDigitosTarjeta: null,
        saldoAnterior: 1000, saldoFinal: 1500, totalCreditos: 500, totalDebitos: 0,
        movimientos: [{ fecha: '2026-12-05', descripcion: 'Abono de ejemplo', monto: 500, tipo: 'CREDITO' }]
      },
      sumaCreditos: 500, sumaDebitos: 0, saldoCalculado: 1500, diferencia: 0,
      estadoCuadre: 'CUADRA', errores: [], advertencias: []
    }
  });

  const pdf = () => new File(['%PDF-1.4'], 'extracto.pdf', { type: 'application/pdf' });

  const subirCon = (r: ResultadoLectura) => {
    api.procesarExtractoIa.and.returnValue(of({ data: r } as any));
    component.seleccionar(pdf());
    component.subir();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['procesarExtractoIa', 'confirmarExtractoIa']);
    await TestBed.configureTestingModule({
      imports: [LecturaIaComponent],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(LecturaIaComponent);
    component = fixture.componentInstance;
    component.idConciliacion = 7;
    emitidos = [];
    component.aprobado.subscribe(j => emitidos.push(j));
    fixture.detectChanges();
  });

  it('si el extracto queda cargado sigue el motor sin pedir confirmación', () => {
    subirCon(resultado('CARGADO', 'Se cargaron 1 movimientos'));

    expect(emitidos).toEqual(['job-1']);
    expect(fixture.nativeElement.textContent).toContain('Se cargaron 1 movimientos');
    expect(fixture.nativeElement.textContent).not.toContain('Cargar de todas formas');
    expect(api.confirmarExtractoIa).not.toHaveBeenCalled();
  });

  it('si se rechaza muestra el motivo y no permite cargar', () => {
    subirCon(resultado('RECHAZADO', 'La lectura no coincide'));

    expect(fixture.nativeElement.textContent).toContain('La lectura no coincide');
    expect(component.puedeConfirmar()).toBeFalse();
    expect(emitidos).toEqual([]);
  });

  it('si requiere confirmación carga una sola vez', () => {
    api.confirmarExtractoIa.and.returnValue(of({ data: 'job-2' } as any));
    subirCon(resultado('REQUIERE_CONFIRMACION', 'No se pudo verificar'));

    component.confirmar();
    component.confirmar();

    expect(emitidos).toEqual(['job-2']);
    expect(api.confirmarExtractoIa).toHaveBeenCalledTimes(1);
  });

  it('acepta extractos en distintos formatos', () => {
    for (const nombre of ['e.xlsx', 'e.XLS', 'e.csv', 'e.txt', 'foto.jpg']) {
      component.seleccionar(new File(['a'], nombre));
      expect(component.archivo?.name).toBe(nombre);
    }
  });

  it('rechaza un tipo de archivo no admitido', () => {
    component.seleccionar(new File(['a'], 'extracto.zip'));

    expect(component.archivo).toBeNull();
    expect(component.error).toContain('Seleccione');
  });

  it('no muestra los movimientos hasta que el usuario los pide', () => {
    subirCon(resultado('CARGADO', 'ok'));

    expect(fixture.nativeElement.textContent).not.toContain('Abono de ejemplo');
    component.detalleAbierto = true;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Abono de ejemplo');
  });

  it('muestra el mensaje cuando falla la lectura', () => {
    api.procesarExtractoIa.and.returnValue(
      throwError(() => ({ error: { message: 'La API de IA no respondió' } })));
    component.seleccionar(pdf());
    component.subir();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('La API de IA no respondió');
    expect(component.leyendo).toBeFalse();
  });
});
