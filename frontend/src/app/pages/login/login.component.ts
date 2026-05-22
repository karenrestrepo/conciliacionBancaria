import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="login-container">
      <div class="login-left">
        <div class="login-brand">
          <div class="brand-logo">CB</div>
          <h1>Conciliación Bancaria</h1>
          <p>Sistema de gestión y conciliación de movimientos bancarios</p>
        </div>
        <div class="login-features">
          <div class="feature-item">
            <mat-icon>verified</mat-icon>
            <span>Motor de conciliación automático</span>
          </div>
          <div class="feature-item">
            <mat-icon>security</mat-icon>
            <span>Control de acceso por roles</span>
          </div>
          <div class="feature-item">
            <mat-icon>analytics</mat-icon>
            <span>Métricas y reportes en tiempo real</span>
          </div>
        </div>
      </div>

      <div class="login-right">
        <mat-card class="login-card">
          <mat-card-header>
            <mat-card-title>Iniciar sesión</mat-card-title>
            <mat-card-subtitle>Ingrese sus credenciales para continuar</mat-card-subtitle>
          </mat-card-header>

          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="onSubmit()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Correo electrónico</mat-label>
                <input matInput type="email" formControlName="email" placeholder="usuario@empresa.com">
                <mat-icon matSuffix>mail_outline</mat-icon>
                <mat-error *ngIf="form.get('email')?.hasError('required')">El correo es requerido</mat-error>
                <mat-error *ngIf="form.get('email')?.hasError('email')">Correo inválido</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Contraseña</mat-label>
                <input matInput [type]="hidePassword ? 'password' : 'text'" formControlName="password">
                <button mat-icon-button matSuffix type="button" (click)="hidePassword = !hidePassword">
                  <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
                </button>
                <mat-error *ngIf="form.get('password')?.hasError('required')">La contraseña es requerida</mat-error>
              </mat-form-field>

              <div class="error-message" *ngIf="errorMsg">
                <mat-icon>error_outline</mat-icon>
                {{ errorMsg }}
              </div>

              <button mat-flat-button color="primary" type="submit"
                      class="submit-btn" [disabled]="loading || form.invalid">
                <mat-spinner diameter="20" *ngIf="loading"></mat-spinner>
                <span *ngIf="!loading">Ingresar</span>
              </button>
            </form>
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      height: 100vh;
      background: #f4f6f9;
    }

    .login-left {
      flex: 1;
      background: #1a2332;
      display: flex;
      flex-direction: column;
      justify-content: center;
      padding: 60px;
      color: #fff;
    }

    .brand-logo {
      width: 56px;
      height: 56px;
      background: #3d7ebf;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 18px;
      margin-bottom: 24px;
    }

    .login-brand h1 {
      font-size: 32px;
      font-weight: 300;
      margin: 0 0 12px;
      letter-spacing: -0.5px;
    }

    .login-brand p {
      font-size: 16px;
      color: #8a9bb0;
      margin: 0 0 48px;
      line-height: 1.6;
    }

    .feature-item {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 20px;
      color: #b0bec5;
    }

    .feature-item mat-icon {
      color: #3d7ebf;
      font-size: 20px;
    }

    .feature-item span { font-size: 15px; }

    .login-right {
      width: 480px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
    }

    .login-card {
      width: 100%;
      padding: 16px;
      box-shadow: 0 4px 24px rgba(0,0,0,0.08) !important;
      border-radius: 12px !important;
    }

    mat-card-title {
      font-size: 22px !important;
      font-weight: 600 !important;
      color: #1a2332 !important;
    }

    mat-card-subtitle {
      font-size: 14px !important;
      color: #6b7a8d !important;
      margin-bottom: 24px !important;
    }

    .full-width {
      width: 100%;
      margin-bottom: 8px;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #e53935;
      font-size: 14px;
      margin-bottom: 16px;
      padding: 12px;
      background: #ffeaea;
      border-radius: 6px;
    }

    .submit-btn {
      width: 100%;
      height: 48px;
      font-size: 15px;
      font-weight: 500;
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      margin-top: 8px;
    }

    .submit-btn:hover { background: #3d7ebf !important; }
  `]
})
export class LoginComponent {
  form: FormGroup;
  loading = false;
  hidePassword = true;
  errorMsg = '';

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', Validators.required]
    });
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.errorMsg = '';

    this.auth.login(this.form.value).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.loading = false;
        this.errorMsg = 'Credenciales incorrectas. Verifique su correo y contraseña.';
      }
    });
  }
}
