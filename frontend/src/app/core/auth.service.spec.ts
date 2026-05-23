import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService, { provide: Router, useValue: routerSpy }]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('debe crearse correctamente', () => {
    expect(service).toBeTruthy();
  });

  describe('isLoggedIn()', () => {
    it('retorna false cuando no hay token', () => {
      expect(service.isLoggedIn()).toBeFalse();
    });

    it('retorna true cuando hay token en localStorage', () => {
      localStorage.setItem('token', 'un-jwt-cualquiera');
      expect(service.isLoggedIn()).toBeTrue();
    });
  });

  describe('hasRole()', () => {
    it('retorna true cuando el rol coincide', () => {
      localStorage.setItem('rol', 'CONTADOR');
      expect(service.hasRole('CONTADOR')).toBeTrue();
    });

    it('retorna true cuando el rol está entre varios permitidos', () => {
      localStorage.setItem('rol', 'FINANZAS');
      expect(service.hasRole('CONTADOR', 'FINANZAS', 'ADMIN')).toBeTrue();
    });

    it('retorna false cuando el rol no coincide', () => {
      localStorage.setItem('rol', 'AUXILIAR');
      expect(service.hasRole('ADMIN')).toBeFalse();
    });

    it('retorna false cuando no hay rol almacenado', () => {
      expect(service.hasRole('CONTADOR')).toBeFalse();
    });
  });

  describe('login()', () => {
    it('guarda token, rol y email en localStorage al recibir respuesta exitosa', () => {
      const mockResponse = {
        success: true,
        message: 'OK',
        data: { token: 'jwt-123', rol: 'CONTADOR', email: 'user@test.com', expiresIn: 3600 },
        timestamp: ''
      };

      service.login({ email: 'user@test.com', password: 'pass' }).subscribe();

      const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/login');
      expect(req.request.method).toBe('POST');
      req.flush(mockResponse);

      expect(localStorage.getItem('token')).toBe('jwt-123');
      expect(localStorage.getItem('rol')).toBe('CONTADOR');
      expect(localStorage.getItem('email')).toBe('user@test.com');
    });

    it('no modifica localStorage cuando success es false', () => {
      const mockResponse = {
        success: false,
        message: 'Credenciales inválidas',
        data: null as any,
        timestamp: ''
      };

      service.login({ email: 'bad@test.com', password: 'wrong' }).subscribe();

      const req = httpMock.expectOne('http://localhost:8080/api/v1/auth/login');
      req.flush(mockResponse);

      expect(localStorage.getItem('token')).toBeNull();
    });
  });

  describe('logout()', () => {
    it('limpia localStorage y navega a /login', () => {
      localStorage.setItem('token', 'jwt-abc');
      localStorage.setItem('rol', 'CONTADOR');

      service.logout();

      expect(localStorage.getItem('token')).toBeNull();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('getToken() / getRol() / getEmail()', () => {
    it('retorna null cuando el almacenamiento está vacío', () => {
      expect(service.getToken()).toBeNull();
      expect(service.getRol()).toBeNull();
      expect(service.getEmail()).toBeNull();
    });

    it('retorna los valores almacenados', () => {
      localStorage.setItem('token', 'tok');
      localStorage.setItem('rol', 'ADMIN');
      localStorage.setItem('email', 'admin@test.com');

      expect(service.getToken()).toBe('tok');
      expect(service.getRol()).toBe('ADMIN');
      expect(service.getEmail()).toBe('admin@test.com');
    });
  });
});
