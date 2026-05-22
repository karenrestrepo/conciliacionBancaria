import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { ApiResponse, LoginRequest, LoginResponse } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = 'http://localhost:8080/api/v1';
  private readonly TOKEN_KEY = 'token';
  private readonly ROL_KEY = 'rol';
  private readonly EMAIL_KEY = 'email';

  constructor(private http: HttpClient, private router: Router) {}

  login(request: LoginRequest): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>(`${this.API}/auth/login`, request).pipe(
      tap(res => {
        if (res.success) {
          localStorage.setItem(this.TOKEN_KEY, res.data.token);
          localStorage.setItem(this.ROL_KEY, res.data.rol);
          localStorage.setItem(this.EMAIL_KEY, res.data.email);
        }
      })
    );
  }

  logout(): void {
    localStorage.clear();
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getRol(): string | null {
    return localStorage.getItem(this.ROL_KEY);
  }

  getEmail(): string | null {
    return localStorage.getItem(this.EMAIL_KEY);
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  hasRole(...roles: string[]): boolean {
    const rol = this.getRol();
    return roles.includes(rol ?? '');
  }
}
