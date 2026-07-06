import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule, AbstractControl } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatStepperModule } from '@angular/material/stepper';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule,
    MatInputModule, MatButtonModule, MatIconModule, MatSelectModule,
    MatStepperModule, MatProgressSpinnerModule, MatDividerModule
  ],
  template: `
    <div class="registro-container">
      <div class="registro-left">
        <div class="brand-logo">CB</div>
        <h1>Registro de Empresa</h1>
        <p>Cree su empresa y su cuenta de administrador para comenzar a usar el sistema de conciliación bancaria.</p>

        <div class="step-info" *ngFor="let s of stepLabels; let i = index">
          <div class="step-dot" [class.active]="i <= currentStep" [class.done]="i < currentStep">
            <mat-icon *ngIf="i < currentStep">check</mat-icon>
            <span *ngIf="i >= currentStep">{{ i + 1 }}</span>
          </div>
          <span [class.active-label]="i === currentStep">{{ s }}</span>
        </div>
      </div>

      <div class="registro-right">
        <mat-card class="registro-card">
          <mat-card-header>
            <mat-card-title>{{ stepLabels[currentStep] }}</mat-card-title>
            <mat-card-subtitle>Paso {{ currentStep + 1 }} de 3</mat-card-subtitle>
          </mat-card-header>

          <mat-card-content>

            <!-- PASO 1: Identificación -->
            <form *ngIf="currentStep === 0" [formGroup]="formId" (ngSubmit)="verificarEmpresa()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Tipo de identificación</mat-label>
                <mat-select formControlName="tipoIdentificacion" (selectionChange)="onTipoChange()">
                  <mat-option value="CEDULA">Cédula de ciudadanía</mat-option>
                  <mat-option value="NIT">NIT</mat-option>
                </mat-select>
                <mat-error>Seleccione un tipo</mat-error>
              </mat-form-field>

              <div class="nit-row" *ngIf="isNit()">
                <mat-form-field appearance="outline" class="nit-numero">
                  <mat-label>Número de NIT</mat-label>
                  <input matInput formControlName="numeroIdentificacion" placeholder="900123456">
                  <mat-error>Ingrese un número válido</mat-error>
                </mat-form-field>
                <span class="nit-sep">–</span>
                <mat-form-field appearance="outline" class="nit-dv">
                  <mat-label>DV</mat-label>
                  <input matInput formControlName="digitoVerificacion" maxlength="1" placeholder="0">
                  <mat-error>Requerido</mat-error>
                </mat-form-field>
              </div>

              <mat-form-field appearance="outline" class="full-width" *ngIf="!isNit()">
                <mat-label>Número de cédula</mat-label>
                <input matInput formControlName="numeroIdentificacion" placeholder="1234567890">
                <mat-error>Ingrese un número válido</mat-error>
              </mat-form-field>

              <div class="error-message" *ngIf="errorMsg">
                <mat-icon>error_outline</mat-icon>{{ errorMsg }}
              </div>

              <button mat-flat-button color="primary" type="submit"
                      class="submit-btn" [disabled]="loading || formId.invalid">
                <mat-spinner diameter="20" *ngIf="loading"></mat-spinner>
                <span *ngIf="!loading">Continuar</span>
              </button>
            </form>

            <!-- PASO 2: Datos de la empresa -->
            <form *ngIf="currentStep === 1" [formGroup]="formEmpresa" (ngSubmit)="nextStep()">
              <div class="id-badge">
                <mat-icon>business</mat-icon>
                <span>{{ formId.value.tipoIdentificacion }} {{ formId.value.numeroIdentificacion }}
                  <span *ngIf="isNit()">–{{ formId.value.digitoVerificacion }}</span>
                </span>
              </div>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Nombre o razón social</mat-label>
                <input matInput formControlName="nombreEmpresa" placeholder="Mi Empresa S.A.S">
                <mat-icon matSuffix>corporate_fare</mat-icon>
                <mat-error>El nombre es requerido</mat-error>
              </mat-form-field>

              <div class="btn-row">
                <button mat-stroked-button type="button" (click)="prevStep()">Atrás</button>
                <button mat-flat-button color="primary" type="submit"
                        class="submit-btn-sm" [disabled]="formEmpresa.invalid">
                  Continuar
                </button>
              </div>
            </form>

            <!-- PASO 3: Datos del administrador -->
            <form *ngIf="currentStep === 2" [formGroup]="formAdmin" (ngSubmit)="registrar()">
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Nombre completo</mat-label>
                <input matInput formControlName="nombreAdmin" placeholder="Juan Pérez">
                <mat-icon matSuffix>person</mat-icon>
                <mat-error>El nombre es requerido</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Correo electrónico</mat-label>
                <input matInput type="email" formControlName="emailAdmin" placeholder="admin@miempresa.com">
                <mat-icon matSuffix>mail_outline</mat-icon>
                <mat-error *ngIf="formAdmin.get('emailAdmin')?.hasError('required')">El correo es requerido</mat-error>
                <mat-error *ngIf="formAdmin.get('emailAdmin')?.hasError('email')">Correo inválido</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Contraseña</mat-label>
                <input matInput [type]="hidePass ? 'password' : 'text'" formControlName="passwordAdmin">
                <button mat-icon-button matSuffix type="button" (click)="hidePass = !hidePass">
                  <mat-icon>{{ hidePass ? 'visibility_off' : 'visibility' }}</mat-icon>
                </button>
                <mat-hint>Mínimo 8 caracteres</mat-hint>
                <mat-error *ngIf="formAdmin.get('passwordAdmin')?.hasError('required')">La contraseña es requerida</mat-error>
                <mat-error *ngIf="formAdmin.get('passwordAdmin')?.hasError('minlength')">Mínimo 8 caracteres</mat-error>
              </mat-form-field>

              <mat-form-field appearance="outline" class="full-width" style="margin-top:8px">
                <mat-label>Confirmar contraseña</mat-label>
                <input matInput [type]="hidePass2 ? 'password' : 'text'" formControlName="confirmarPassword">
                <button mat-icon-button matSuffix type="button" (click)="hidePass2 = !hidePass2">
                  <mat-icon>{{ hidePass2 ? 'visibility_off' : 'visibility' }}</mat-icon>
                </button>
                <mat-error *ngIf="formAdmin.hasError('passwordMismatch')">Las contraseñas no coinciden</mat-error>
              </mat-form-field>

              <div class="error-message" *ngIf="errorMsg">
                <mat-icon>error_outline</mat-icon>{{ errorMsg }}
              </div>

              <div class="btn-row">
                <button mat-stroked-button type="button" (click)="prevStep()">Atrás</button>
                <button mat-flat-button color="primary" type="submit"
                        class="submit-btn-sm" [disabled]="loading || formAdmin.invalid">
                  <mat-spinner diameter="20" *ngIf="loading"></mat-spinner>
                  <span *ngIf="!loading">Crear empresa</span>
                </button>
              </div>
            </form>

            <mat-divider style="margin: 20px 0 12px"></mat-divider>
            <p class="login-link">
              ¿Ya tiene cuenta?
              <a (click)="irLogin()" class="link">Iniciar sesión</a>
            </p>
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .registro-container {
      display: flex;
      height: 100vh;
      background: #f4f6f9;
    }

    .registro-left {
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

    .registro-left h1 {
      font-size: 28px;
      font-weight: 300;
      margin: 0 0 12px;
    }

    .registro-left p {
      font-size: 15px;
      color: #8a9bb0;
      margin: 0 0 48px;
      line-height: 1.6;
    }

    .step-info {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 20px;
      color: #8a9bb0;
      font-size: 14px;
    }

    .step-dot {
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: rgba(255,255,255,0.1);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 13px;
      font-weight: 600;
      flex-shrink: 0;
    }

    .step-dot.active { background: #3d7ebf; color: #fff; }
    .step-dot.done { background: #27ae60; color: #fff; }
    .step-dot mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .active-label { color: #fff; font-weight: 500; }

    .registro-right {
      width: 520px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px;
    }

    .registro-card {
      width: 100%;
      padding: 16px;
      box-shadow: 0 4px 24px rgba(0,0,0,0.08) !important;
      border-radius: 12px !important;
    }

    mat-card-title {
      font-size: 20px !important;
      font-weight: 600 !important;
      color: #1a2332 !important;
    }

    mat-card-subtitle {
      font-size: 13px !important;
      color: #6b7a8d !important;
      margin-bottom: 20px !important;
    }

    .full-width { width: 100%; margin-bottom: 8px; }

    .nit-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
    }

    .nit-numero { flex: 1; }
    .nit-dv { width: 80px; }
    .nit-sep { font-size: 20px; color: #6b7a8d; margin-top: -20px; }

    .id-badge {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      background: #e8f0fe;
      border-radius: 8px;
      margin-bottom: 16px;
      color: #1a2332;
      font-size: 14px;
      font-weight: 500;
    }

    .id-badge mat-icon { color: #3d7ebf; font-size: 18px; width: 18px; height: 18px; }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #e53935;
      font-size: 13px;
      margin-bottom: 12px;
      padding: 10px 12px;
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

    .btn-row {
      display: flex;
      gap: 12px;
      margin-top: 8px;
    }

    .btn-row button:first-child { flex: 0 0 auto; }

    .submit-btn-sm {
      flex: 1;
      height: 48px;
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
    }

    .submit-btn-sm:hover { background: #3d7ebf !important; }

    .login-link {
      text-align: center;
      font-size: 13px;
      color: #6b7a8d;
      margin: 0;
    }

    .link {
      color: #3d7ebf;
      cursor: pointer;
      font-weight: 500;
      text-decoration: none;
    }

    .link:hover { text-decoration: underline; }
  `]
})
export class RegistroComponent {
  currentStep = 0;
  stepLabels = ['Identificación', 'Datos de la empresa', 'Administrador'];

  formId: FormGroup;
  formEmpresa: FormGroup;
  formAdmin: FormGroup;

  loading = false;
  hidePass = true;
  hidePass2 = true;
  errorMsg = '';

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router
  ) {
    this.formId = this.fb.group({
      tipoIdentificacion: ['', Validators.required],
      numeroIdentificacion: ['', [Validators.required, Validators.pattern(/^\d+$/)]],
      digitoVerificacion: ['']
    });

    this.formEmpresa = this.fb.group({
      nombreEmpresa: ['', Validators.required]
    });

    this.formAdmin = this.fb.group({
      nombreAdmin: ['', Validators.required],
      emailAdmin: ['', [Validators.required, Validators.email]],
      passwordAdmin: ['', [Validators.required, Validators.minLength(8)]],
      confirmarPassword: ['', Validators.required]
    }, { validators: this.passwordMatch });
  }

  isNit(): boolean {
    return this.formId.get('tipoIdentificacion')?.value === 'NIT';
  }

  onTipoChange(): void {
    const dvCtrl = this.formId.get('digitoVerificacion')!;
    if (this.isNit()) {
      dvCtrl.setValidators([Validators.required, Validators.pattern(/^\d$/)]);
    } else {
      dvCtrl.clearValidators();
      dvCtrl.setValue('');
    }
    dvCtrl.updateValueAndValidity();
  }

  verificarEmpresa(): void {
    if (this.formId.invalid) return;
    this.loading = true;
    this.errorMsg = '';

    const { tipoIdentificacion, numeroIdentificacion, digitoVerificacion } = this.formId.value;
    this.auth.verificarEmpresa({ tipoIdentificacion, numeroIdentificacion, digitoVerificacion })
      .subscribe({
        next: res => {
          this.loading = false;
          if (res.data.existe) {
            this.errorMsg = 'Ya existe una empresa registrada con este número de identificación.';
          } else {
            this.currentStep = 1;
          }
        },
        error: () => {
          this.loading = false;
          this.errorMsg = 'Error al verificar. Intente de nuevo.';
        }
      });
  }

  nextStep(): void {
    if (this.currentStep === 1 && this.formEmpresa.valid) {
      this.currentStep = 2;
    }
  }

  prevStep(): void {
    if (this.currentStep > 0) this.currentStep--;
    this.errorMsg = '';
  }

  registrar(): void {
    if (this.formAdmin.invalid) return;
    this.loading = true;
    this.errorMsg = '';

    const { tipoIdentificacion, numeroIdentificacion, digitoVerificacion } = this.formId.value;
    const { nombreEmpresa } = this.formEmpresa.value;
    const { nombreAdmin, emailAdmin, passwordAdmin } = this.formAdmin.value;

    this.auth.registro({
      tipoIdentificacion,
      numeroIdentificacion,
      digitoVerificacion: digitoVerificacion || undefined,
      nombreEmpresa,
      nombreAdmin,
      emailAdmin,
      passwordAdmin
    }).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        this.errorMsg = err?.error?.message ?? 'Error al registrar. Intente de nuevo.';
      }
    });
  }

  irLogin(): void {
    this.router.navigate(['/login']);
  }

  private passwordMatch(control: AbstractControl) {
    const pwd = control.get('passwordAdmin')?.value;
    const confirm = control.get('confirmarPassword')?.value;
    return pwd === confirm ? null : { passwordMismatch: true };
  }
}
