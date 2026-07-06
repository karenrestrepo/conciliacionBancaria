import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import {
  ApiResponse, LoginRequest, LoginResponse,
  Permiso, RegistroRequest, VerificarEmpresaRequest
} from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = 'http://localhost:8080/api/v1';
  private readonly TOKEN_KEY = 'token';
  private readonly ROL_KEY = 'rol';
  private readonly EMAIL_KEY = 'email';
  private readonly EMPRESA_ID_KEY = 'empresaId';
  private readonly EMPRESA_NOMBRE_KEY = 'empresaNombre';
  private readonly PERMISOS_KEY = 'permisos';

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(`${this.API}/auth/login`, request).pipe(
      tap(res => {
        if (res.success) {
          this.guardarSesion(res.data);
        }
      })
    );
  }

  verificarEmpresa(request: VerificarEmpresaRequest): Observable<ApiResponse<{ existe: boolean }>> {
    return this.http.post<ApiResponse<{ existe: boolean }>>(
      `${this.API}/auth/verificar-empresa`, request);
  }

  registro(request: RegistroRequest): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(`${this.API}/auth/registro`, request).pipe(
      tap(res => {
        if (res.success) {
          this.guardarSesion(res.data);
        }
      })
    );
  }

  private guardarSesion(data: LoginResponse): void {
    localStorage.setItem(this.TOKEN_KEY, data.token);
    localStorage.setItem(this.ROL_KEY, data.rol);
    localStorage.setItem(this.EMAIL_KEY, data.email);
    localStorage.setItem(this.EMPRESA_ID_KEY, data.empresaId?.toString() ?? '');
    localStorage.setItem(this.EMPRESA_NOMBRE_KEY, data.nombreEmpresa ?? '');
    localStorage.setItem(this.PERMISOS_KEY, JSON.stringify(data.permisos ?? []));
  }

  logout(): void {
    localStorage.clear();
    this.router.navigate(['/login']);
  }

  getToken(): string | null { return localStorage.getItem(this.TOKEN_KEY); }
  getRol(): string | null { return localStorage.getItem(this.ROL_KEY); }
  getEmail(): string | null { return localStorage.getItem(this.EMAIL_KEY); }
  getEmpresaId(): number | null {
    const v = localStorage.getItem(this.EMPRESA_ID_KEY);
    return v ? Number(v) : null;
  }
  getEmpresaNombre(): string | null { return localStorage.getItem(this.EMPRESA_NOMBRE_KEY); }

  getPermisos(): Permiso[] {
    try {
      return JSON.parse(localStorage.getItem(this.PERMISOS_KEY) ?? '[]');
    } catch { return []; }
  }

  isLoggedIn(): boolean { return !!this.getToken(); }

  hasRole(...roles: string[]): boolean {
    return roles.includes(this.getRol() ?? '');
  }

  hasPermiso(permiso: Permiso): boolean {
    return this.getPermisos().includes(permiso);
  }

  isAdmin(): boolean { return this.getRol() === 'ADMIN'; }
}
