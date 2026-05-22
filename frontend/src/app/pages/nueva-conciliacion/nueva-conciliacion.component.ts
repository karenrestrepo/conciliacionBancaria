import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../../core/api.service';
import { Conciliacion } from '../../core/models';
import { interval, Subscription } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';

@Component({
  selector: 'app-nueva-conciliacion',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatProgressSpinnerModule, MatStepperModule,
    MatDividerModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">{{ conciliacion ? 'Cargar Archivos' : 'Nueva Conciliación' }}</h1>
          <p class="page-subtitle">Complete los pasos para iniciar el proceso de conciliación</p>
        </div>
        <button mat-button routerLink="/conciliaciones" class="back-btn">
          <mat-icon>arrow_back</mat-icon>
          Volver
        </button>
      </div>

      <mat-stepper [linear]="true" #stepper orientation="horizontal" class="stepper">

        <!-- PASO 1: Crear conciliación -->
        <mat-step [completed]="!!conciliacion" label="Período">
          <mat-card class="step-card">
            <mat-card-header>
              <mat-card-title>Definir período de conciliación</mat-card-title>
              <mat-card-subtitle>Ingrese el período en formato YYYY-MM</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <form [formGroup]="periodoForm" (ngSubmit)="crearConciliacion()">
                <mat-form-field appearance="outline" class="periodo-field">
                  <mat-label>Período</mat-label>
                  <input matInput formControlName="periodo"
                         placeholder="2024-01" [disabled]="!!conciliacion">
                  <mat-hint>Ejemplo: 2024-01 para enero de 2024</mat-hint>
                  <mat-error *ngIf="periodoForm.get('periodo')?.hasError('pattern')">
                    Formato inválido. Use YYYY-MM
                  </mat-error>
                </mat-form-field>

                <div class="error-message" *ngIf="errorPeriodo">
                  <mat-icon>error_outline</mat-icon>{{ errorPeriodo }}
                </div>

                <div class="success-info" *ngIf="conciliacion">
                  <mat-icon>check_circle</mat-icon>
                  Conciliación {{ conciliacion.periodo }} creada con ID #{{ conciliacion.id }}
                </div>

                <div class="step-actions">
                  <button mat-flat-button class="primary-btn" type="submit"
                          [disabled]="periodoForm.invalid || loadingPeriodo || !!conciliacion">
                    <mat-spinner diameter="18" *ngIf="loadingPeriodo"></mat-spinner>
                    <span *ngIf="!loadingPeriodo">Crear Conciliación</span>
                  </button>
                  <button mat-flat-button class="next-btn" type="button"
                          *ngIf="conciliacion" matStepperNext>
                    Continuar
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-card-content>
          </mat-card>
        </mat-step>

        <!-- PASO 2: Cargar extracto bancario -->
        <mat-step [completed]="extractoCargado" label="Extracto Bancario">
          <mat-card class="step-card">
            <mat-card-header>
              <mat-card-title>Cargar extracto bancario</mat-card-title>
              <mat-card-subtitle>Archivo CSV con los movimientos del banco</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="csv-format-info">
                <mat-icon>info_outline</mat-icon>
                <span>Columnas requeridas: <strong>fecha, descripcion, monto, tipo_movimiento</strong></span>
              </div>

              <div class="upload-area" (click)="extractoInput.click()"
                   [class.has-file]="extractoFile">
                <mat-icon>{{ extractoFile ? 'description' : 'upload_file' }}</mat-icon>
                <span *ngIf="!extractoFile">Haga clic para seleccionar el archivo CSV</span>
                <span *ngIf="extractoFile">{{ extractoFile.name }}</span>
              </div>
              <input #extractoInput type="file" accept=".csv"
                     (change)="onExtractoSelected($event)" hidden>

              <div class="job-progress" *ngIf="jobProgreso > 0 || jobEstado === 'IN_PROGRESS'">
                <div class="job-header">
                  <span>Motor de conciliación</span>
                  <span>{{ jobProgreso }}%</span>
                </div>
                <mat-progress-bar mode="determinate" [value]="jobProgreso"
                                  [color]="jobEstado === 'FAILED' ? 'warn' : 'primary'">
                </mat-progress-bar>
                <span class="job-status" [ngClass]="getJobClass()">{{ getJobLabel() }}</span>
              </div>

              <div class="success-info" *ngIf="extractoCargado">
                <mat-icon>check_circle</mat-icon>
                Extracto bancario cargado y motor completado
              </div>

              <div class="error-message" *ngIf="errorExtracto">
                <mat-icon>error_outline</mat-icon>{{ errorExtracto }}
              </div>

              <div class="step-actions">
                <button mat-flat-button class="primary-btn"
                        (click)="cargarExtracto()"
                        [disabled]="!extractoFile || loadingExtracto || extractoCargado">
                  <mat-spinner diameter="18" *ngIf="loadingExtracto"></mat-spinner>
                  <span *ngIf="!loadingExtracto">Cargar y Procesar</span>
                </button>
                <button mat-flat-button class="next-btn"
                        *ngIf="extractoCargado" matStepperNext>
                  Continuar
                  <mat-icon>arrow_forward</mat-icon>
                </button>
              </div>
            </mat-card-content>
          </mat-card>
        </mat-step>

        <!-- PASO 3: Cargar libro auxiliar -->
        <mat-step [completed]="auxiliarCargado" label="Libro Auxiliar">
          <mat-card class="step-card">
            <mat-card-header>
              <mat-card-title>Cargar libro auxiliar contable</mat-card-title>
              <mat-card-subtitle>Archivo CSV con los movimientos contables</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="csv-format-info">
                <mat-icon>info_outline</mat-icon>
                <span>Columnas requeridas: <strong>fecha, descripcion, monto, tipo_movimiento</strong></span>
              </div>

              <div class="upload-area" (click)="auxiliarInput.click()"
                   [class.has-file]="auxiliarFile">
                <mat-icon>{{ auxiliarFile ? 'description' : 'upload_file' }}</mat-icon>
                <span *ngIf="!auxiliarFile">Haga clic para seleccionar el archivo CSV</span>
                <span *ngIf="auxiliarFile">{{ auxiliarFile.name }}</span>
              </div>
              <input #auxiliarInput type="file" accept=".csv"
                     (change)="onAuxiliarSelected($event)" hidden>

              <div class="success-info" *ngIf="auxiliarCargado">
                <mat-icon>check_circle</mat-icon>
                Libro auxiliar cargado correctamente
              </div>

              <div class="error-message" *ngIf="errorAuxiliar">
                <mat-icon>error_outline</mat-icon>{{ errorAuxiliar }}
              </div>

              <div class="step-actions">
                <button mat-flat-button class="primary-btn"
                        (click)="cargarAuxiliar()"
                        [disabled]="!auxiliarFile || loadingAuxiliar || auxiliarCargado">
                  <mat-spinner diameter="18" *ngIf="loadingAuxiliar"></mat-spinner>
                  <span *ngIf="!loadingAuxiliar">Cargar Libro Auxiliar</span>
                </button>
                <button mat-flat-button class="next-btn"
                        *ngIf="auxiliarCargado" matStepperNext>
                  Continuar
                  <mat-icon>arrow_forward</mat-icon>
                </button>
              </div>
            </mat-card-content>
          </mat-card>
        </mat-step>

        <!-- PASO 4: Completado -->
        <mat-step label="Completado">
          <mat-card class="step-card complete-card">
            <mat-card-content>
              <div class="complete-state">
                <div class="complete-icon">
                  <mat-icon>check_circle</mat-icon>
                </div>
                <h2>Proceso iniciado correctamente</h2>
                <p>La conciliación del período <strong>{{ conciliacion?.periodo }}</strong>
                   ha sido creada y los archivos han sido cargados.</p>
                <p>Puede revisar las sugerencias generadas por el motor de conciliación.</p>
                <div class="complete-actions">
                  <button mat-flat-button class="primary-btn"
                          [routerLink]="['/sugerencias', conciliacion?.id]">
                    <mat-icon>rate_review</mat-icon>
                    Revisar Sugerencias
                  </button>
                  <button mat-stroked-button routerLink="/conciliaciones" class="secondary-btn">
                    Ver todas las conciliaciones
                  </button>
                </div>
              </div>
            </mat-card-content>
          </mat-card>
        </mat-step>
      </mat-stepper>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 900px; }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 32px;
    }

    .page-title { font-size: 26px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 14px; color: #6b7a8d; margin: 0; }

    .back-btn { color: #6b7a8d; gap: 4px; }

    .stepper { background: transparent !important; }

    .step-card {
      margin-top: 24px;
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
    }

    mat-card-title { font-size: 16px !important; font-weight: 600 !important; color: #1a2332 !important; }
    mat-card-subtitle { font-size: 13px !important; color: #6b7a8d !important; }

    .periodo-field { width: 280px; margin-top: 16px; }

    .csv-format-info {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #f0f7ff;
      border: 1px solid #bfdbfe;
      border-radius: 8px;
      padding: 12px 16px;
      margin: 16px 0;
      font-size: 13px;
      color: #1e40af;
    }

    .csv-format-info mat-icon { font-size: 18px; color: #3d7ebf; }

    .upload-area {
      border: 2px dashed #d1d5db;
      border-radius: 10px;
      padding: 40px;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s;
      color: #6b7a8d;
      margin: 8px 0 16px;
    }

    .upload-area:hover { border-color: #3d7ebf; background: #f0f7ff; color: #3d7ebf; }

    .upload-area.has-file {
      border-color: #22c55e;
      background: #f0fdf4;
      color: #166534;
    }

    .upload-area mat-icon { display: block; font-size: 36px; width: 36px; height: 36px; margin: 0 auto 8px; }

    .job-progress { margin: 16px 0; }

    .job-header {
      display: flex;
      justify-content: space-between;
      font-size: 13px;
      color: #6b7a8d;
      margin-bottom: 8px;
    }

    .job-status {
      display: block;
      font-size: 12px;
      margin-top: 6px;
    }

    .job-pending { color: #6b7a8d; }
    .job-progress-status { color: #3d7ebf; }
    .job-completed { color: #22c55e; }
    .job-failed { color: #e53935; }

    .success-info {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #166534;
      background: #f0fdf4;
      border: 1px solid #bbf7d0;
      border-radius: 8px;
      padding: 12px 16px;
      font-size: 14px;
      margin: 12px 0;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #e53935;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 8px;
      padding: 12px 16px;
      font-size: 14px;
      margin: 12px 0;
    }

    .step-actions {
      display: flex;
      gap: 12px;
      margin-top: 20px;
      align-items: center;
    }

    .primary-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 42px;
      gap: 6px;
    }

    .next-btn {
      background: #3d7ebf !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 42px;
      gap: 6px;
    }

    .secondary-btn {
      border-color: #1a2332 !important;
      color: #1a2332 !important;
      border-radius: 8px !important;
      height: 42px;
    }

    .complete-card { text-align: center; }

    .complete-state { padding: 40px 20px; }

    .complete-icon mat-icon {
      font-size: 64px;
      width: 64px;
      height: 64px;
      color: #22c55e;
    }

    .complete-state h2 { font-size: 22px; color: #1a2332; margin: 16px 0 8px; }
    .complete-state p { color: #6b7a8d; font-size: 15px; margin: 0 0 8px; }

    .complete-actions {
      display: flex;
      gap: 12px;
      justify-content: center;
      margin-top: 32px;
    }
  `]
})
export class NuevaConciliacionComponent implements OnInit {
  periodoForm: FormGroup;
  conciliacion: Conciliacion | null = null;
  extractoFile: File | null = null;
  auxiliarFile: File | null = null;
  extractoCargado = false;
  auxiliarCargado = false;
  loadingPeriodo = false;
  loadingExtracto = false;
  loadingAuxiliar = false;
  errorPeriodo = '';
  errorExtracto = '';
  errorAuxiliar = '';
  jobProgreso = 0;
  jobEstado = '';
  private pollSub?: Subscription;

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.periodoForm = this.fb.group({
      periodo: ['', [Validators.required,
        Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/)]]
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.api.obtenerConciliacion(+id).subscribe({
        next: res => { this.conciliacion = res.data; }
      });
    }
  }

  crearConciliacion(): void {
    if (this.periodoForm.invalid) return;
    this.loadingPeriodo = true;
    this.errorPeriodo = '';
    this.api.crearConciliacion(this.periodoForm.value.periodo).subscribe({
      next: res => {
        this.conciliacion = res.data;
        this.loadingPeriodo = false;
      },
      error: err => {
        this.loadingPeriodo = false;
        this.errorPeriodo = err.error?.message ?? 'Error al crear la conciliación';
      }
    });
  }

  onExtractoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.extractoFile = input.files[0];
  }

  onAuxiliarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.auxiliarFile = input.files[0];
  }

  cargarExtracto(): void {
    if (!this.extractoFile || !this.conciliacion) return;
    this.loadingExtracto = true;
    this.errorExtracto = '';
    this.api.cargarExtracto(this.conciliacion.id, this.extractoFile).subscribe({
      next: res => {
        this.loadingExtracto = false;
        this.iniciarPolling(res.data);
      },
      error: err => {
        this.loadingExtracto = false;
        this.errorExtracto = err.error?.message ?? 'Error al cargar el extracto';
      }
    });
  }

  iniciarPolling(jobId: string): void {
    this.jobEstado = 'PENDING';
    this.pollSub = interval(2000).pipe(
      switchMap(() => this.api.jobStatus(jobId)),
      takeWhile(res => res.data.estado !== 'COMPLETED' && res.data.estado !== 'FAILED', true)
    ).subscribe({
      next: res => {
        this.jobProgreso = res.data.progreso;
        this.jobEstado = res.data.estado;
        if (res.data.estado === 'COMPLETED') {
          this.extractoCargado = true;
          this.pollSub?.unsubscribe();
        }
        if (res.data.estado === 'FAILED') {
          this.errorExtracto = res.data.mensajeError ?? 'Error en el motor de conciliación';
          this.pollSub?.unsubscribe();
        }
      }
    });
  }

  cargarAuxiliar(): void {
    if (!this.auxiliarFile || !this.conciliacion) return;
    this.loadingAuxiliar = true;
    this.errorAuxiliar = '';
    this.api.cargarAuxiliar(this.conciliacion.id, this.auxiliarFile).subscribe({
      next: () => {
        this.loadingAuxiliar = false;
        this.auxiliarCargado = true;
      },
      error: err => {
        this.loadingAuxiliar = false;
        this.errorAuxiliar = err.error?.message ?? 'Error al cargar el libro auxiliar';
      }
    });
  }

  getJobClass(): string {
    const map: Record<string, string> = {
      'PENDING': 'job-pending',
      'IN_PROGRESS': 'job-progress-status',
      'COMPLETED': 'job-completed',
      'FAILED': 'job-failed'
    };
    return map[this.jobEstado] ?? '';
  }

  getJobLabel(): string {
    const map: Record<string, string> = {
      'PENDING': 'En espera...',
      'IN_PROGRESS': 'Procesando movimientos...',
      'COMPLETED': 'Motor completado',
      'FAILED': 'Error en el procesamiento'
    };
    return map[this.jobEstado] ?? '';
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }
}
