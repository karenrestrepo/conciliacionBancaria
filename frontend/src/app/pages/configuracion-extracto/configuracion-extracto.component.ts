import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule } from '@angular/material/stepper';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Banco, Cuenta, ConfiguracionExtracto, TipoArchivoExtracto } from '../../core/models';

@Component({
  selector: 'app-configuracion-extracto',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatStepperModule,
    MatCheckboxModule, MatTableModule, MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <button mat-button routerLink="/bancos" class="back-btn">
            <mat-icon>arrow_back</mat-icon>
            Bancos
          </button>
          <h1 class="page-title">Configuración de Extractos</h1>
          <p class="page-subtitle">Define cómo parsear archivos de extracto para cada banco</p>
        </div>
      </div>

      <!-- Stepper de configuración -->
      <mat-card class="stepper-card" *ngIf="canManage()">
        <mat-card-header>
          <mat-card-title>{{ editandoId ? 'Editar configuración' : 'Nueva configuración' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-stepper [linear]="!editandoId" #stepper orientation="horizontal" class="stepper">

            <!-- PASO 1: Banco -->
            <mat-step [stepControl]="bancoForm" label="Banco">
              <form [formGroup]="bancoForm" class="step-form">
                <mat-form-field appearance="outline" class="full-field">
                  <mat-label>Banco</mat-label>
                  <mat-select formControlName="idBanco" (selectionChange)="onBancoChange($event.value)">
                    <mat-option *ngFor="let b of bancos" [value]="b.id">
                      {{ b.nombre }}{{ b.codigo ? ' (' + b.codigo + ')' : '' }}
                    </mat-option>
                  </mat-select>
                  <mat-error *ngIf="bancoForm.get('idBanco')?.hasError('required')">
                    Seleccione un banco
                  </mat-error>
                </mat-form-field>

                <div class="step-actions">
                  <button mat-flat-button class="next-btn" matStepperNext
                          [disabled]="bancoForm.invalid">
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 2: Nombre -->
            <mat-step [stepControl]="nombreForm" label="Nombre">
              <form [formGroup]="nombreForm" class="step-form">
                <mat-form-field appearance="outline" class="full-field">
                  <mat-label>Nombre de la configuración</mat-label>
                  <input matInput formControlName="nombre"
                         placeholder="Ej: Extracto mensual CSV">
                  <mat-error *ngIf="nombreForm.get('nombre')?.hasError('required')">
                    El nombre es obligatorio
                  </mat-error>
                  <mat-error *ngIf="nombreForm.get('nombre')?.hasError('maxlength')">
                    Máximo 200 caracteres
                  </mat-error>
                </mat-form-field>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="next-btn" matStepperNext
                          [disabled]="nombreForm.invalid">
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 3: Tipo de archivo -->
            <mat-step [stepControl]="tipoForm" label="Tipo archivo">
              <form [formGroup]="tipoForm" class="step-form">
                <mat-form-field appearance="outline" class="full-field">
                  <mat-label>Tipo de archivo</mat-label>
                  <mat-select formControlName="tipoArchivo" (selectionChange)="onTipoArchivoChange()">
                    <mat-option value="CSV">CSV</mat-option>
                    <mat-option value="TXT">TXT</mat-option>
                    <mat-option value="XLS">XLS</mat-option>
                    <mat-option value="XLSX">XLSX</mat-option>
                    <mat-option value="PDF">PDF (sin configuración de columnas)</mat-option>
                  </mat-select>
                  <mat-error *ngIf="tipoForm.get('tipoArchivo')?.hasError('required')">
                    Seleccione un tipo de archivo
                  </mat-error>
                </mat-form-field>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="next-btn" matStepperNext
                          [disabled]="tipoForm.invalid">
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 4: Detalle de configuración (omitir para PDF) -->
            <mat-step label="Detalle">
              <form [formGroup]="detalleForm" class="step-form">
                <div *ngIf="tipoArchivo === 'PDF'" class="pdf-notice">
                  <mat-icon>info_outline</mat-icon>
                  <span>Los archivos PDF no requieren configuración de columnas.</span>
                </div>

                <ng-container *ngIf="tipoArchivo !== 'PDF'">
                  <div class="form-grid">
                    <mat-form-field appearance="outline">
                      <mat-label>Separador</mat-label>
                      <input matInput formControlName="separador" placeholder=",">
                      <mat-hint>Para CSV/TXT: ',' ';' '|' '\\t'</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Filas a saltar</mat-label>
                      <input matInput type="number" formControlName="filasASaltar" min="0">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Encoding</mat-label>
                      <mat-select formControlName="encoding">
                        <mat-option value="UTF-8">UTF-8</mat-option>
                        <mat-option value="ISO-8859-1">ISO-8859-1 (Latin-1)</mat-option>
                        <mat-option value="windows-1252">Windows-1252</mat-option>
                      </mat-select>
                    </mat-form-field>

                    <mat-form-field appearance="outline" *ngIf="esExcel()">
                      <mat-label>Número de hoja</mat-label>
                      <input matInput type="number" formControlName="numeroHoja" min="0">
                      <mat-hint>Índice 0 = primera hoja</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna fecha</mat-label>
                      <input matInput type="number" formControlName="columnaFecha" min="0">
                      <mat-hint>Índice 0 = primera columna</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Formato fecha</mat-label>
                      <input matInput formControlName="formatoFecha" placeholder="dd/MM/yyyy">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna descripción</mat-label>
                      <input matInput type="number" formControlName="columnaDescripcion" min="0">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna referencia</mat-label>
                      <input matInput type="number" formControlName="columnaReferencia" min="0">
                    </mat-form-field>
                  </div>

                  <div class="checkbox-row">
                    <mat-checkbox formControlName="tieneEncabezado">
                      El archivo tiene fila de encabezado
                    </mat-checkbox>
                  </div>

                  <div class="checkbox-row">
                    <mat-checkbox formControlName="debitoYCreditoSeparados"
                                  (change)="onDebitosCreditosSeparadosChange()">
                      Débito y crédito en columnas separadas
                    </mat-checkbox>
                  </div>

                  <div class="form-grid" *ngIf="!detalleForm.get('debitoYCreditoSeparados')?.value">
                    <mat-form-field appearance="outline">
                      <mat-label>Columna monto</mat-label>
                      <input matInput type="number" formControlName="columnaMonto" min="0">
                    </mat-form-field>
                  </div>

                  <div class="form-grid" *ngIf="detalleForm.get('debitoYCreditoSeparados')?.value">
                    <mat-form-field appearance="outline">
                      <mat-label>Columna débito</mat-label>
                      <input matInput type="number" formControlName="columnaDebito" min="0">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna crédito</mat-label>
                      <input matInput type="number" formControlName="columnaCredito" min="0">
                    </mat-form-field>
                  </div>
                </ng-container>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="next-btn" matStepperNext>
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 5: Cuentas asociadas -->
            <mat-step label="Cuentas">
              <form [formGroup]="cuentasForm" class="step-form">
                <div class="checkbox-row">
                  <mat-checkbox formControlName="aplicaParaTodasLasCuentas"
                                (change)="onAplicaTodosChange()">
                    Aplica para todas las cuentas del banco
                  </mat-checkbox>
                </div>

                <mat-form-field appearance="outline" class="full-field"
                                *ngIf="!cuentasForm.get('aplicaParaTodasLasCuentas')?.value">
                  <mat-label>Cuentas específicas</mat-label>
                  <mat-select formControlName="idsCuentas" multiple>
                    <mat-option *ngFor="let c of cuentas" [value]="c.id">
                      {{ c.numeroCuenta }}{{ c.descripcion ? ' — ' + c.descripcion : '' }}
                    </mat-option>
                  </mat-select>
                  <mat-hint>Seleccione una o más cuentas</mat-hint>
                </mat-form-field>

                <div class="error-message" *ngIf="errorGuardar">
                  <mat-icon>error_outline</mat-icon>{{ errorGuardar }}
                </div>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="save-btn" (click)="guardar()" [disabled]="saving">
                    <mat-spinner diameter="18" *ngIf="saving"></mat-spinner>
                    <mat-icon *ngIf="!saving">save</mat-icon>
                    <span>{{ saving ? 'Guardando...' : (editandoId ? 'Actualizar' : 'Guardar') }}</span>
                  </button>
                </div>
              </form>
            </mat-step>

          </mat-stepper>
        </mat-card-content>
      </mat-card>

      <!-- Lista de configuraciones existentes -->
      <mat-card class="table-card" *ngIf="idBancoSeleccionado">
        <mat-card-header>
          <mat-card-title>Configuraciones para {{ nombreBancoSeleccionado }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loadingLista" class="loading-container">
            <mat-spinner diameter="36"></mat-spinner>
          </div>

          <div *ngIf="!loadingLista && configuraciones.length === 0" class="empty-state">
            <mat-icon>settings</mat-icon>
            <p>No hay configuraciones para este banco</p>
          </div>

          <table mat-table [dataSource]="configuraciones"
                 *ngIf="!loadingLista && configuraciones.length > 0" class="data-table">

            <ng-container matColumnDef="nombre">
              <th mat-header-cell *matHeaderCellDef>Nombre</th>
              <td mat-cell *matCellDef="let row">
                <span class="config-nombre">{{ row.nombre }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="tipoArchivo">
              <th mat-header-cell *matHeaderCellDef>Tipo</th>
              <td mat-cell *matCellDef="let row">
                <span class="tipo-chip tipo-{{ row.tipoArchivo.toLowerCase() }}">
                  {{ row.tipoArchivo }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="cuentas">
              <th mat-header-cell *matHeaderCellDef>Cuentas</th>
              <td mat-cell *matCellDef="let row">
                <span *ngIf="row.aplicaParaTodasLasCuentas" class="todas-chip">Todas</span>
                <span *ngIf="!row.aplicaParaTodasLasCuentas">
                  {{ row.idsCuentas?.length ?? 0 }} cuenta(s)
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="activo">
              <th mat-header-cell *matHeaderCellDef>Estado</th>
              <td mat-cell *matCellDef="let row">
                <span class="activo-chip" [ngClass]="row.activo ? 'activo' : 'inactivo'">
                  {{ row.activo ? 'Activa' : 'Inactiva' }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="acciones">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row">
                <button mat-icon-button class="edit-btn" (click)="editar(row)"
                        matTooltip="Editar" *ngIf="canManage()">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button class="delete-btn" (click)="eliminar(row)"
                        matTooltip="Eliminar" *ngIf="canManage()">
                  <mat-icon>delete_outline</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;" class="data-row"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 960px; }

    .page-header { margin-bottom: 24px; }

    .back-btn { color: #6b7a8d; gap: 4px; margin-bottom: 8px; padding: 0; }

    .page-title { font-size: 26px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 14px; color: #6b7a8d; margin: 0; }

    .stepper-card, .table-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      margin-bottom: 24px;
    }

    mat-card-title { font-size: 15px !important; font-weight: 600 !important; color: #1a2332 !important; }

    .stepper { background: transparent; }

    .step-form { padding: 24px 0 8px; }

    .full-field { width: 100%; max-width: 480px; }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 12px;
    }

    .checkbox-row {
      margin: 12px 0;
    }

    .pdf-notice {
      display: flex;
      align-items: center;
      gap: 10px;
      background: #e8f4fd;
      border: 1px solid #90caf9;
      border-radius: 8px;
      padding: 14px 18px;
      color: #1565c0;
      font-size: 14px;
      margin-bottom: 16px;
    }

    .step-actions {
      display: flex;
      gap: 12px;
      margin-top: 24px;
      align-items: center;
    }

    .next-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      gap: 6px;
    }

    .back-step-btn { color: #6b7a8d; gap: 4px; }

    .save-btn {
      background: #3d7ebf !important;
      color: #fff !important;
      border-radius: 8px !important;
      gap: 6px;
      display: flex;
      align-items: center;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #e53935;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 8px;
      padding: 10px 14px;
      font-size: 13px;
      margin-top: 8px;
      margin-bottom: 8px;
    }

    .loading-container { display: flex; justify-content: center; padding: 60px; }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 40px; color: #6b7a8d; gap: 12px;
    }

    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: #b0bec5; }

    .data-table { width: 100%; }

    .mat-mdc-header-row { background: #f8fafc; }

    .mat-mdc-header-cell {
      font-size: 12px !important; font-weight: 600 !important;
      color: #6b7a8d !important; text-transform: uppercase; letter-spacing: 0.5px;
    }

    .mat-mdc-cell { font-size: 14px; color: #1a2332; padding: 12px 16px !important; }

    .data-row:hover { background: #f8fafc; }

    .config-nombre { font-weight: 500; }

    .tipo-chip {
      padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500;
    }
    .tipo-csv   { background: #dbeafe; color: #1e40af; }
    .tipo-txt   { background: #f3f4f6; color: #374151; }
    .tipo-xls   { background: #dcfce7; color: #166534; }
    .tipo-xlsx  { background: #d1fae5; color: #065f46; }
    .tipo-pdf   { background: #fee2e2; color: #991b1b; }

    .todas-chip {
      background: #fef3c7; color: #92400e;
      padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500;
    }

    .activo-chip { padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500; }
    .activo   { background: #dcfce7; color: #166534; }
    .inactivo { background: #fee2e2; color: #991b1b; }

    .edit-btn { color: #3d7ebf; }
    .delete-btn { color: #e53935; }
  `]
})
export class ConfiguracionExtractoComponent implements OnInit {

  bancos: Banco[] = [];
  cuentas: Cuenta[] = [];
  configuraciones: ConfiguracionExtracto[] = [];

  bancoForm: FormGroup;
  nombreForm: FormGroup;
  tipoForm: FormGroup;
  detalleForm: FormGroup;
  cuentasForm: FormGroup;

  idBancoSeleccionado: number | null = null;
  nombreBancoSeleccionado = '';
  tipoArchivo: TipoArchivoExtracto | null = null;

  editandoId: number | null = null;
  saving = false;
  loadingLista = false;
  errorGuardar = '';

  columns = ['nombre', 'tipoArchivo', 'cuentas', 'activo', 'acciones'];

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.bancoForm = this.fb.group({
      idBanco: [null, Validators.required]
    });
    this.nombreForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(200)]]
    });
    this.tipoForm = this.fb.group({
      tipoArchivo: [null, Validators.required]
    });
    this.detalleForm = this.fb.group({
      separador: [''],
      filasASaltar: [0],
      tieneEncabezado: [true],
      columnaFecha: [null],
      formatoFecha: ['dd/MM/yyyy'],
      columnaDescripcion: [null],
      columnaReferencia: [null],
      columnaMonto: [null],
      columnaDebito: [null],
      columnaCredito: [null],
      debitoYCreditoSeparados: [false],
      encoding: ['UTF-8'],
      numeroHoja: [0]
    });
    this.cuentasForm = this.fb.group({
      aplicaParaTodasLasCuentas: [true],
      idsCuentas: [[]]
    });
  }

  ngOnInit(): void {
    this.api.listarBancos().subscribe({
      next: res => {
        this.bancos = res.data;
        const idBancoParam = this.route.snapshot.paramMap.get('idBanco');
        if (idBancoParam) {
          const id = Number(idBancoParam);
          this.bancoForm.patchValue({ idBanco: id });
          this.onBancoChange(id);
        }
      }
    });
  }

  onBancoChange(idBanco: number): void {
    this.idBancoSeleccionado = idBanco;
    const banco = this.bancos.find(b => b.id === idBanco);
    this.nombreBancoSeleccionado = banco?.nombre ?? '';
    this.cargarCuentas(idBanco);
    this.cargarConfiguraciones(idBanco);
  }

  onTipoArchivoChange(): void {
    this.tipoArchivo = this.tipoForm.get('tipoArchivo')?.value as TipoArchivoExtracto;
  }

  onDebitosCreditosSeparadosChange(): void {
    // campos se muestran/ocultan via *ngIf
  }

  onAplicaTodosChange(): void {
    // cuentas field se muestra/oculta via *ngIf
  }

  esExcel(): boolean {
    return this.tipoArchivo === 'XLS' || this.tipoArchivo === 'XLSX';
  }

  cargarCuentas(idBanco: number): void {
    this.api.listarCuentasPorBanco(idBanco).subscribe({
      next: res => { this.cuentas = res.data; }
    });
  }

  cargarConfiguraciones(idBanco: number): void {
    this.loadingLista = true;
    this.api.listarConfiguracionesPorBanco(idBanco).subscribe({
      next: res => { this.configuraciones = res.data; this.loadingLista = false; },
      error: () => { this.loadingLista = false; }
    });
  }

  guardar(): void {
    this.errorGuardar = '';
    this.saving = true;

    const idBanco = this.bancoForm.get('idBanco')?.value;
    const nombre = this.nombreForm.get('nombre')?.value;
    const tipoArchivo = this.tipoForm.get('tipoArchivo')?.value;
    const aplicaParaTodasLasCuentas = this.cuentasForm.get('aplicaParaTodasLasCuentas')?.value;
    const idsCuentas = aplicaParaTodasLasCuentas ? [] : (this.cuentasForm.get('idsCuentas')?.value ?? []);

    let configuracionDetalle: any = null;
    if (tipoArchivo !== 'PDF') {
      configuracionDetalle = { ...this.detalleForm.value };
      if (!configuracionDetalle.debitoYCreditoSeparados) {
        delete configuracionDetalle.columnaDebito;
        delete configuracionDetalle.columnaCredito;
      } else {
        delete configuracionDetalle.columnaMonto;
      }
      if (!this.esExcel()) {
        delete configuracionDetalle.numeroHoja;
      }
    }

    const payload = {
      idBanco,
      nombre,
      tipoArchivo,
      aplicaParaTodasLasCuentas,
      idsCuentas,
      configuracionDetalle
    };

    const obs = this.editandoId
      ? this.api.actualizarConfiguracion(this.editandoId, payload)
      : this.api.crearConfiguracion(payload);

    obs.subscribe({
      next: res => {
        if (this.editandoId) {
          this.configuraciones = this.configuraciones.map(c =>
            c.id === this.editandoId ? res.data : c
          );
        } else {
          this.configuraciones = [...this.configuraciones, res.data];
        }
        this.resetForms();
        this.saving = false;
      },
      error: err => {
        this.saving = false;
        this.errorGuardar = err.error?.message ?? 'Error al guardar la configuración';
      }
    });
  }

  editar(config: ConfiguracionExtracto): void {
    this.editandoId = config.id;
    this.bancoForm.patchValue({ idBanco: config.idBanco });
    this.nombreForm.patchValue({ nombre: config.nombre });
    this.tipoForm.patchValue({ tipoArchivo: config.tipoArchivo });
    this.tipoArchivo = config.tipoArchivo;
    this.cuentasForm.patchValue({
      aplicaParaTodasLasCuentas: config.aplicaParaTodasLasCuentas,
      idsCuentas: config.idsCuentas ?? []
    });

    if (config.configuracionDetalle) {
      try {
        const detalle = JSON.parse(config.configuracionDetalle);
        this.detalleForm.patchValue(detalle);
      } catch (_) { /* ignore */ }
    }
  }

  eliminar(config: ConfiguracionExtracto): void {
    this.api.eliminarConfiguracion(config.id).subscribe({
      next: () => {
        this.configuraciones = this.configuraciones.filter(c => c.id !== config.id);
      },
      error: err => {
        this.errorGuardar = err.error?.message ?? 'Error al eliminar la configuración';
      }
    });
  }

  resetForms(): void {
    this.editandoId = null;
    this.bancoForm.reset();
    this.nombreForm.reset();
    this.tipoForm.reset();
    this.detalleForm.reset({
      filasASaltar: 0, tieneEncabezado: true, formatoFecha: 'dd/MM/yyyy',
      debitoYCreditoSeparados: false, encoding: 'UTF-8', numeroHoja: 0
    });
    this.cuentasForm.reset({ aplicaParaTodasLasCuentas: true, idsCuentas: [] });
    this.tipoArchivo = null;
  }

  canManage(): boolean {
    return this.auth.hasRole('CONTADOR', 'ADMIN');
  }
}
