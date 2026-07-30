import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../../core/api.service';
import { Banco, Cuenta, Conciliacion, ConfiguracionExtracto, ResumenCargaAuxiliar } from '../../core/models';
import { interval, Subscription } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';

interface GrupoTarjetas {
  idRepresentante: number;
  cuentas: Cuenta[];
}

@Component({
  selector: 'app-nueva-conciliacion',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, MatCardModule,
    MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule,
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
              <mat-card-subtitle>Seleccione el banco y el período en formato YYYY-MM</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <form [formGroup]="periodoForm" (ngSubmit)="crearConciliacion()">

                <mat-form-field appearance="outline" class="banco-field">
                  <mat-label>Banco</mat-label>
                  <mat-select formControlName="idBanco"
                              [disabled]="!!conciliacion"
                              (selectionChange)="onBancoChange($event.value)">
                    <mat-option *ngFor="let b of bancos" [value]="b.id">
                      {{ b.nombre }}{{ b.codigo ? ' (' + b.codigo + ')' : '' }}
                    </mat-option>
                  </mat-select>
                  <mat-error *ngIf="periodoForm.get('idBanco')?.hasError('required')">
                    Seleccione un banco
                  </mat-error>
                </mat-form-field>

                <mat-form-field appearance="outline" class="cuenta-field">
                  <mat-label>Cuenta</mat-label>
                  <mat-select formControlName="idCuenta" [disabled]="!!conciliacion || cuentas.length === 0">
                    <mat-option *ngIf="cuentas.length === 0" [value]="null" disabled>
                      — Seleccione primero un banco —
                    </mat-option>

                    <!-- Cuentas regulares (no auxiliarConjunto) -->
                    <mat-option *ngFor="let c of cuentasRegulares" [value]="c.id">
                      {{ c.numeroCuenta }} ({{ getTipoLabel(c.tipo) }})
                      {{ c.descripcion ? ' — ' + c.descripcion : '' }}
                    </mat-option>

                    <!-- Grupo de tarjetas con auxiliar conjunto (una sola entrada) -->
                    <mat-option *ngIf="grupoTarjetas" [value]="grupoTarjetas.idRepresentante"
                                class="opcion-tarjetas-conjunto">
                      <div class="opcion-tc-inner">
                        <mat-icon class="tc-icon">credit_card</mat-icon>
                        <span>
                          Tarjetas de Crédito (Auxiliar Conjunto)
                          <span class="tc-count">{{ grupoTarjetas.cuentas.length }} tarjeta(s)</span>
                        </span>
                      </div>
                    </mat-option>
                  </mat-select>
                  <mat-error *ngIf="periodoForm.get('idCuenta')?.hasError('required')">
                    Seleccione una cuenta
                  </mat-error>
                </mat-form-field>

                <!-- Aviso tarjetas conjuntas seleccionadas -->
                <div class="tc-aviso" *ngIf="esTarjetasConjuntasSeleccionado()">
                  <mat-icon>info_outline</mat-icon>
                  <span>
                    Se creará una conciliación para las {{ grupoTarjetas?.cuentas?.length }} tarjetas de crédito del banco.
                    Podrá subir un extracto por cada tarjeta.
                  </span>
                </div>

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
                  Conciliación {{ conciliacion.periodo }} — {{ conciliacion.numeroCuenta }}
                  ({{ conciliacion.nombreBanco }}) creada con ID #{{ conciliacion.id }}
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
              <mat-card-title>
                {{ conciliacion?.auxiliarConjunto ? 'Agregar extractos bancarios' : 'Cargar extracto bancario' }}
              </mat-card-title>
              <mat-card-subtitle>
                {{ configuracionExtracto
                    ? 'Configuración: ' + configuracionExtracto.nombre + ' — ' + configuracionExtracto.tipoArchivo
                    : 'Archivo con los movimientos del banco' }}
              </mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <!-- Aviso tarjeta de crédito con auxiliar conjunto -->
              <div class="csv-format-info" *ngIf="conciliacion?.auxiliarConjunto">
                <mat-icon>credit_card</mat-icon>
                <span>
                  Esta cuenta tiene <strong>auxiliar conjunto</strong>. Puede subir múltiples extractos
                  (uno por tarjeta). Cada extracto se agrega sin borrar los anteriores.
                </span>
              </div>

              <!-- Con configuración XLSX/XLS -->
              <div class="csv-format-info" *ngIf="configuracionExtracto">
                <mat-icon>check_circle</mat-icon>
                <span>
                  Se usará la configuración <strong>{{ configuracionExtracto.nombre }}</strong>
                  para parsear el archivo <strong>{{ configuracionExtracto.tipoArchivo }}</strong>.
                </span>
              </div>
              <!-- Sin configuración: formato CSV estándar -->
              <div class="csv-format-info csv-format-warn" *ngIf="!configuracionExtracto && conciliacion">
                <mat-icon>warning_amber</mat-icon>
                <span>
                  No se encontró configuración de extracto para este banco.
                  Se esperará un CSV con columnas: <strong>fecha, descripcion, monto, tipo_movimiento</strong>.
                </span>
              </div>
              <div class="csv-format-info" *ngIf="!conciliacion">
                <mat-icon>info_outline</mat-icon>
                <span>Columnas requeridas (CSV): <strong>fecha, descripcion, monto, tipo_movimiento</strong></span>
              </div>

              <div class="upload-area" (click)="extractoInput.click()"
                   [class.has-file]="extractoFile || extractoFilesPendientes.length > 0">
                <mat-icon>{{ (extractoFile || extractoFilesPendientes.length > 0) ? 'description' : 'upload_file' }}</mat-icon>
                <span *ngIf="!extractoFile && extractoFilesPendientes.length === 0">
                  Haga clic para seleccionar
                  {{ configuracionExtracto ? 'el archivo ' + configuracionExtracto.tipoArchivo : 'el archivo CSV' }}
                  <ng-container *ngIf="conciliacion?.auxiliarConjunto"> (puede seleccionar varios)</ng-container>
                </span>
                <span *ngIf="extractoFile && !conciliacion?.auxiliarConjunto">{{ extractoFile.name }}</span>
                <span *ngIf="conciliacion?.auxiliarConjunto && extractoFilesPendientes.length > 0">
                  {{ extractoFilesPendientes.length }} archivo(s) seleccionado(s)
                </span>
              </div>
              <input #extractoInput type="file" [attr.accept]="acceptExtracto"
                     [attr.multiple]="conciliacion?.auxiliarConjunto ? true : null"
                     (change)="onExtractoSelected($event, extractoInput)" hidden>

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
                <ng-container *ngIf="conciliacion?.auxiliarConjunto; else singleSuccess">
                  {{ extractosAgregados }} extracto(s) procesado(s).
                  <span *ngIf="extractoFilesPendientes.length > 0"> Procesando {{ extractoFilesPendientes.length }} restante(s)…</span>
                  <span *ngIf="extractoFilesPendientes.length === 0"> Puede seleccionar más o continuar.</span>
                </ng-container>
                <ng-template #singleSuccess>
                  Extracto bancario cargado y motor completado
                </ng-template>
              </div>

              <div class="error-message" *ngIf="errorExtracto">
                <mat-icon>error_outline</mat-icon>{{ errorExtracto }}
              </div>

              <div class="step-actions">
                <button mat-flat-button class="primary-btn"
                        (click)="cargarExtracto()"
                        [disabled]="(conciliacion?.auxiliarConjunto ? extractoFilesPendientes.length === 0 : !extractoFile) || loadingExtracto || (extractoCargado && !conciliacion?.auxiliarConjunto)">
                  <mat-spinner diameter="18" *ngIf="loadingExtracto"></mat-spinner>
                  <span *ngIf="!loadingExtracto">
                    {{ conciliacion?.auxiliarConjunto
                        ? (extractoFilesPendientes.length > 1 ? 'Agregar ' + extractoFilesPendientes.length + ' extractos' : 'Agregar extracto')
                        : 'Cargar y Procesar' }}
                  </span>
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
              <mat-card-subtitle>Archivo CSV estándar o exportación SIESA (.xls/.xlsx)</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <div class="csv-format-info">
                <mat-icon>info_outline</mat-icon>
                <span>
                  <strong>CSV estándar:</strong> columnas fecha, descripcion, monto, tipo_movimiento &nbsp;|&nbsp;
                  <strong>SIESA XLS:</strong> archivo exportado directamente desde SIESA (auxiliar de movimientos)
                </span>
              </div>

              <div class="upload-area" (click)="auxiliarInput.click()"
                   [class.has-file]="auxiliarFile">
                <mat-icon>{{ auxiliarFile ? 'description' : 'upload_file' }}</mat-icon>
                <span *ngIf="!auxiliarFile">Haga clic para seleccionar el archivo</span>
                <span *ngIf="auxiliarFile">{{ auxiliarFile.name }}</span>
              </div>
              <input #auxiliarInput type="file" accept=".csv,.xls,.xlsx"
                     (change)="onAuxiliarSelected($event)" hidden>

              <div class="success-info" *ngIf="auxiliarCargado">
                <mat-icon>check_circle</mat-icon>
                <span *ngIf="!resumenAuxiliar">Libro auxiliar cargado correctamente</span>
                <span *ngIf="resumenAuxiliar">{{ resumenAuxiliarTexto() }}</span>
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

    .banco-field { width: 260px; margin-top: 16px; }
    .cuenta-field { width: 340px; margin-top: 16px; }
    .periodo-field { width: 240px; margin-top: 16px; }

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
    .csv-format-warn { background: #fffbeb; border-color: #fde68a; color: #92400e; }
    .csv-format-warn mat-icon { color: #d97706; }

    .tc-aviso {
      display: flex; align-items: flex-start; gap: 8px;
      background: #fce7f3; border: 1px solid #fbcfe8;
      border-radius: 8px; padding: 10px 14px;
      font-size: 13px; color: #9d174d; margin-top: 8px;
    }
    .tc-aviso mat-icon { font-size: 18px; color: #be185d; flex-shrink: 0; }

    .opcion-tc-inner {
      display: flex; align-items: center; gap: 8px;
    }
    .tc-icon { font-size: 18px; color: #9d174d; }
    .tc-count {
      margin-left: 6px; font-size: 11px; color: #9d174d;
      background: #fce7f3; padding: 1px 6px; border-radius: 10px;
    }

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
export class NuevaConciliacionComponent implements OnInit, OnDestroy {
  periodoForm: FormGroup;
  conciliacion: Conciliacion | null = null;
  bancos: Banco[] = [];
  cuentas: Cuenta[] = [];
  cuentasRegulares: Cuenta[] = [];
  grupoTarjetas: GrupoTarjetas | null = null;
  configuracionExtracto: ConfiguracionExtracto | null = null;
  extractoFile: File | null = null;
  extractoFilesPendientes: File[] = [];
  auxiliarFile: File | null = null;
  extractoCargado = false;
  extractosAgregados = 0;
  auxiliarCargado = false;
  resumenAuxiliar: ResumenCargaAuxiliar | null = null;
  loadingPeriodo = false;
  loadingExtracto = false;
  loadingAuxiliar = false;
  errorPeriodo = '';
  errorExtracto = '';
  errorAuxiliar = '';
  jobProgreso = 0;
  jobEstado = '';
  private pollSub?: Subscription;
  private extractoInputEl: HTMLInputElement | null = null;

  get acceptExtracto(): string {
    if (!this.configuracionExtracto) return '.csv';
    const tipo = this.configuracionExtracto.tipoArchivo.toLowerCase();
    const map: Record<string, string> = {
      csv: '.csv', txt: '.txt', xls: '.xls', xlsx: '.xlsx', pdf: '.pdf'
    };
    return map[tipo] ?? '.csv,.xls,.xlsx,.txt';
  }

  constructor(
    private fb: FormBuilder,
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.periodoForm = this.fb.group({
      idBanco:  [null, Validators.required],
      idCuenta: [null, Validators.required],
      periodo:  ['', [Validators.required,
        Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/)]]
    });
  }

  ngOnInit(): void {
    this.api.listarBancos().subscribe({
      next: res => { this.bancos = res.data; }
    });
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.api.obtenerConciliacion(+id).subscribe({
        next: res => {
          this.conciliacion = res.data;
          if (res.data.idBanco) {
            this.cargarConfiguracionExtracto(res.data.idBanco, res.data.idCuenta);
          }
        }
      });
    }
  }

  onBancoChange(idBanco: number): void {
    this.cuentas = [];
    this.cuentasRegulares = [];
    this.grupoTarjetas = null;
    this.configuracionExtracto = null;
    this.periodoForm.patchValue({ idCuenta: null });
    if (!idBanco) return;
    this.api.listarCuentasPorBanco(idBanco).subscribe({
      next: res => {
        this.cuentas = res.data;
        this.clasificarCuentas(res.data);
      }
    });
    this.cargarConfiguracionExtracto(idBanco, null);
  }

  private clasificarCuentas(cuentas: Cuenta[]): void {
    const tarjetasConjuntas = cuentas.filter(
      c => c.tipo === 'TARJETA_CREDITO' && c.auxiliarConjunto
    );
    this.cuentasRegulares = cuentas.filter(
      c => !(c.tipo === 'TARJETA_CREDITO' && c.auxiliarConjunto)
    );
    this.grupoTarjetas = tarjetasConjuntas.length > 0
      ? { idRepresentante: tarjetasConjuntas[0].id, cuentas: tarjetasConjuntas }
      : null;
  }

  esTarjetasConjuntasSeleccionado(): boolean {
    const idSeleccionado = this.periodoForm.get('idCuenta')?.value;
    return !!idSeleccionado && idSeleccionado === this.grupoTarjetas?.idRepresentante;
  }

  cargarConfiguracionExtracto(idBanco: number, idCuenta: number | null): void {
    this.api.listarConfiguracionesPorBanco(idBanco).subscribe({
      next: res => {
        const activas = res.data.filter(c => c.activo);
        // Preferir configuración específica de la cuenta; si no, la general
        const especifica = idCuenta
          ? activas.find(c => !c.aplicaParaTodasLasCuentas && c.idsCuentas?.includes(idCuenta))
          : null;
        this.configuracionExtracto = especifica
          ?? activas.find(c => c.aplicaParaTodasLasCuentas)
          ?? activas[0]
          ?? null;
      }
    });
  }

  crearConciliacion(): void {
    if (this.periodoForm.invalid) return;
    this.loadingPeriodo = true;
    this.errorPeriodo = '';
    const { periodo, idCuenta } = this.periodoForm.value;
    this.api.crearConciliacion(periodo, idCuenta).subscribe({
      next: res => {
        this.conciliacion = res.data;
        this.loadingPeriodo = false;
        // Refinar la config con la cuenta ya conocida
        const idBanco = this.periodoForm.get('idBanco')?.value;
        if (idBanco) this.cargarConfiguracionExtracto(idBanco, idCuenta);
      },
      error: err => {
        this.loadingPeriodo = false;
        this.errorPeriodo = err.error?.message ?? 'Error al crear la conciliación';
      }
    });
  }

  getTipoLabel(tipo: string): string {
    const map: Record<string, string> = {
      CORRIENTE: 'Cte', AHORRO: 'Aho', FIDUCIARIA: 'Fid', OTRA: 'Otra'
    };
    return map[tipo] ?? tipo;
  }

  onExtractoSelected(event: Event, inputEl: HTMLInputElement): void {
    this.extractoInputEl = inputEl;
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    if (this.conciliacion?.auxiliarConjunto) {
      this.extractoFilesPendientes = Array.from(input.files);
      this.extractoFile = null;
    } else {
      this.extractoFile = input.files[0];
    }
  }

  onAuxiliarSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.auxiliarFile = input.files[0];
  }

  cargarExtracto(): void {
    if (!this.conciliacion) return;
    if (this.conciliacion.auxiliarConjunto) {
      if (!this.extractoFilesPendientes.length) return;
      this.subirSiguienteExtracto();
    } else {
      if (!this.extractoFile) return;
      this.loadingExtracto = true;
      this.errorExtracto = '';
      this.api.cargarExtracto(this.conciliacion.id, this.extractoFile).subscribe({
        next: res => { this.loadingExtracto = false; this.iniciarPolling(res.data); },
        error: err => {
          this.loadingExtracto = false;
          this.errorExtracto = err.error?.message ?? 'Error al cargar el extracto';
        }
      });
    }
  }

  private subirSiguienteExtracto(): void {
    if (!this.conciliacion || !this.extractoFilesPendientes.length) return;
    const archivo = this.extractoFilesPendientes[0];
    this.loadingExtracto = true;
    this.errorExtracto = '';
    this.api.cargarExtracto(this.conciliacion.id, archivo).subscribe({
      next: res => { this.loadingExtracto = false; this.iniciarPolling(res.data); },
      error: err => {
        this.loadingExtracto = false;
        this.errorExtracto = `Error en "${archivo.name}": ` + (err.error?.message ?? 'Error al cargar');
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
          this.extractosAgregados++;
          this.pollSub?.unsubscribe();
          if (this.conciliacion?.auxiliarConjunto) {
            this.extractoFilesPendientes = this.extractoFilesPendientes.slice(1);
            this.jobProgreso = 0;
            this.jobEstado = 'PENDING';
            if (this.extractoFilesPendientes.length > 0) {
              this.subirSiguienteExtracto();
            } else {
              if (this.extractoInputEl) this.extractoInputEl.value = '';
            }
          }
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
      next: res => {
        this.loadingAuxiliar = false;
        this.auxiliarCargado = true;
        this.resumenAuxiliar = res.data;
      },
      error: err => {
        this.loadingAuxiliar = false;
        this.errorAuxiliar = err.error?.message ?? 'Error al cargar el libro auxiliar';
      }
    });
  }

  resumenAuxiliarTexto(): string {
    const r = this.resumenAuxiliar;
    if (!r) return 'Libro auxiliar cargado correctamente';
    const partes = [`${r.nuevos} movimiento(s) nuevo(s)`];
    if (r.anulados > 0) {
      partes.push(`${r.anulados} anulado(s)` + (r.revertidos > 0 ? ` (${r.revertidos} conciliación(es) revertida(s))` : ''));
    }
    return partes.join(', ');
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
