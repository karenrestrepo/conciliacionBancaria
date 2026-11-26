import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ResultadoAuxiliar, ResumenCargaAuxiliar } from '../../core/models';
import { LecturaAuxiliarIaComponent } from './lectura-auxiliar-ia.component';

describe('LecturaAuxiliarIaComponent', () => {
  let fixture: ComponentFixture<LecturaAuxiliarIaComponent>;
  let component: LecturaAuxiliarIaComponent;
  let api: jasmine.SpyObj<ApiService>;
  let emitidos: ResumenCargaAuxiliar[];

  const resumen: ResumenCargaAuxiliar = { jobId: 'job-1', nuevos: 1, anulados: 0, revertidos: 0 };

  const resultado = (estado: ResultadoAuxiliar['estado'], mensaje: string): ResultadoAuxiliar => ({
    estado,
    mensaje,
    resumen: estado === 'CARGADO' ? resumen : null,
    lectura: null,
    revision: {
      mapeo: { columnaFecha: 0 },
      movimientos: [{ fecha: '2026-12-05', descripcion: 'Recibo de ejemplo', monto: 500, tipo: 'CREDITO' }],
      sumaDebitos: 500, sumaCreditos: 0, totalDebitosArchivo: 500, totalCreditosArchivo: 0,
      saldoInicial: null, saldoFinal: null,
      estadoCuadre: 'CUADRA', errores: [], advertencias: []
    }
  });

  const excel = () => new File(['datos'], 'auxiliar.xlsx');

  const subirCon = (r: ResultadoAuxiliar) => {
    api.procesarAuxiliarIa.and.returnValue(of({ data: r } as any));
    component.seleccionar(excel());
    component.subir();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<ApiService>('ApiService', ['procesarAuxiliarIa', 'confirmarAuxiliarIa']);
    await TestBed.configureTestingModule({
      imports: [LecturaAuxiliarIaComponent],
      providers: [{ provide: ApiService, useValue: api }]
    }).compileComponents();
    fixture = TestBed.createComponent(LecturaAuxiliarIaComponent);
    component = fixture.componentInstance;
    component.idConciliacion = 7;
    emitidos = [];
    component.cargado.subscribe(r => emitidos.push(r));
    fixture.detectChanges();
  });

  it('si el auxiliar queda cargado avisa sin pedir confirmación', () => {
    subirCon(resultado('CARGADO', 'El auxiliar cuadra'));

    expect(emitidos).toEqual([resumen]);
    expect(fixture.nativeElement.textContent).toContain('El auxiliar cuadra');
    expect(fixture.nativeElement.textContent).not.toContain('Cargar de todas formas');
  });

  it('si se rechaza no permite cargar', () => {
    subirCon(resultado('RECHAZADO', 'No coincide con los totales'));

    expect(emitidos).toEqual([]);
    expect(component.puedeConfirmar()).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('No coincide con los totales');
  });

  it('si no se puede verificar, confirma con el mismo archivo y mapeo una sola vez', () => {
    api.confirmarAuxiliarIa.and.returnValue(of({ data: resumen } as any));
    subirCon(resultado('REQUIERE_CONFIRMACION', 'Sin totales'));

    component.confirmar();
    component.confirmar();

    expect(api.confirmarAuxiliarIa).toHaveBeenCalledOnceWith(7, jasmine.any(File), { columnaFecha: 0 }, null);
    expect(emitidos).toEqual([resumen]);
  });

  it('muestra el mensaje cuando falla el servicio', () => {
    api.procesarAuxiliarIa.and.returnValue(throwError(() => ({ error: { message: 'Servicio no disponible' } })));
    component.seleccionar(excel());

    component.subir();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Servicio no disponible');
    expect(component.leyendo).toBeFalse();
  });

  it('en un documento confirma devolviendo la lectura ya hecha', () => {
    api.confirmarAuxiliarIa.and.returnValue(of({ data: resumen } as any));
    const r = resultado('REQUIERE_CONFIRMACION', 'Sin totales');
    r.revision.mapeo = null;
    r.lectura = { movimientos: [] };
    api.procesarAuxiliarIa.and.returnValue(of({ data: r } as any));
    component.seleccionar(new File(['%PDF'], 'auxiliar.pdf'));
    component.subir();

    component.confirmar();

    expect(api.confirmarAuxiliarIa).toHaveBeenCalledOnceWith(7, jasmine.any(File), null, { movimientos: [] });
  });

  it('no acepta otros tipos de archivo', () => {
    component.seleccionar(new File(['x'], 'auxiliar.docx'));

    expect(component.archivo).toBeNull();
    expect(component.error).toContain('PDF, Excel');
  });
});
