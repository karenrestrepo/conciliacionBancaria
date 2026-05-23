import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;
  let authSpy: jasmine.SpyObj<AuthService>;
  const BASE = 'http://localhost:8080/api/v1';

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', ['getToken']);
    authSpy.getToken.and.returnValue('test-token');

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ApiService, { provide: AuthService, useValue: authSpy }]
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('debe crearse correctamente', () => {
    expect(service).toBeTruthy();
  });

  describe('listarConciliaciones()', () => {
    it('realiza GET a /conciliaciones con header Authorization', () => {
      service.listarConciliaciones().subscribe();
      const req = httpMock.expectOne(`${BASE}/conciliaciones`);
      expect(req.request.method).toBe('GET');
      expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
      req.flush({ success: true, data: [], message: '', timestamp: '' });
    });
  });

  describe('crearConciliacion()', () => {
    it('realiza POST a /conciliaciones con el periodo en el body', () => {
      service.crearConciliacion('2025-01').subscribe();
      const req = httpMock.expectOne(`${BASE}/conciliaciones`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ periodo: '2025-01' });
      req.flush({ success: true, data: {}, message: '', timestamp: '' });
    });
  });

  describe('obtenerConciliacion()', () => {
    it('realiza GET a /conciliaciones/:id', () => {
      service.obtenerConciliacion(42).subscribe();
      const req = httpMock.expectOne(`${BASE}/conciliaciones/42`);
      expect(req.request.method).toBe('GET');
      req.flush({ success: true, data: {}, message: '', timestamp: '' });
    });
  });

  describe('obtenerMetricas()', () => {
    it('realiza GET a /metricas/resumen', () => {
      service.obtenerMetricas().subscribe();
      const req = httpMock.expectOne(`${BASE}/metricas/resumen`);
      expect(req.request.method).toBe('GET');
      req.flush({ success: true, data: {}, message: '', timestamp: '' });
    });
  });

  describe('obtenerSugerencias()', () => {
    it('realiza GET a /conciliaciones/:id/sugerencias', () => {
      service.obtenerSugerencias(5).subscribe();
      const req = httpMock.expectOne(`${BASE}/conciliaciones/5/sugerencias`);
      expect(req.request.method).toBe('GET');
      req.flush({ success: true, data: [], message: '', timestamp: '' });
    });
  });

  describe('aceptarSugerencia()', () => {
    it('realiza POST a /conciliaciones/:id/sugerencias/:sid/aceptar', () => {
      service.aceptarSugerencia(1, 10).subscribe();
      const req = httpMock.expectOne(`${BASE}/conciliaciones/1/sugerencias/10/aceptar`);
      expect(req.request.method).toBe('POST');
      req.flush({ success: true, data: {}, message: '', timestamp: '' });
    });
  });

  describe('rechazarSugerencia()', () => {
    it('realiza POST a /conciliaciones/:id/sugerencias/:sid/rechazar', () => {
      service.rechazarSugerencia(1, 10).subscribe();
      const req = httpMock.expectOne(`${BASE}/conciliaciones/1/sugerencias/10/rechazar`);
      expect(req.request.method).toBe('POST');
      req.flush({ success: true, data: {}, message: '', timestamp: '' });
    });
  });

  describe('pasarARevision()', () => {
    it('realiza POST a /conciliaciones/:id/revision', () => {
      service.pasarARevision(3).subscribe();
      const req = httpMock.expectOne(`${BASE}/conciliaciones/3/revision`);
      expect(req.request.method).toBe('POST');
      req.flush({ success: true, data: {}, message: '', timestamp: '' });
    });
  });
});
