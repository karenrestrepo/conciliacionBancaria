import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Banco, Cuenta, TipoCuenta } from '../../core/models';

@Component({
  selector: 'app-cuentas',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, MatCardModule, MatTableModule,
    MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatSelectModule, MatProgressSpinnerModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <button mat-button routerLink="/bancos" class="back-btn">
            <mat-icon>arrow_back</mat-icon>
            Bancos
          </button>
          <h1 class="page-title">
            Cuentas de {{ banco?.nombre ?? '...' }}
          </h1>
          <p class="page-subtitle">Gestión de cuentas bancarias para este banco</p>
        </div>
      </div>

      <!-- Formulario agregar cuenta (solo CONTADOR / ADMIN) -->
      <mat-card class="form-card" *ngIf="canCreate()">
        <mat-card-header>
          <mat-card-title>Agregar cuenta</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="cuentaForm" (ngSubmit)="crearCuenta()" class="cuenta-form">

            <mat-form-field appearance="outline" class="field-numero">
              <mat-label>Número de cuenta</mat-label>
              <input matInput formControlName="numeroCuenta" placeholder="123-456789-00">
              <mat-error *ngIf="cuentaForm.get('numeroCuenta')?.hasError('required')">
                El número de cuenta es obligatorio
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="field-tipo">
              <mat-label>Tipo</mat-label>
              <mat-select formControlName="tipo">
                <mat-option value="CORRIENTE">Corriente</mat-option>
                <mat-option value="AHORRO">Ahorro</mat-option>
                <mat-option value="FIDUCIARIA">Fiduciaria</mat-option>
                <mat-option value="OTRA">Otra</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="field-desc">
              <mat-label>Descripción (opcional)</mat-label>
              <input matInput formControlName="descripcion" placeholder="Cuenta principal pagos">
            </mat-form-field>

            <button mat-flat-button class="save-btn" type="submit"
                    [disabled]="cuentaForm.invalid || saving">
              <mat-spinner diameter="18" *ngIf="saving"></mat-spinner>
              <mat-icon *ngIf="!saving">add</mat-icon>
              <span>{{ saving ? 'Guardando...' : 'Agregar' }}</span>
            </button>
          </form>

          <div class="error-message" *ngIf="errorCrear">
            <mat-icon>error_outline</mat-icon>{{ errorCrear }}
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Tabla de cuentas -->
      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-container">
          <mat-spinner diameter="36"></mat-spinner>
        </div>

        <div *ngIf="!loading && cuentas.length === 0" class="empty-state">
          <mat-icon>credit_card</mat-icon>
          <p>No hay cuentas registradas para este banco</p>
        </div>

        <table mat-table [dataSource]="cuentas"
               *ngIf="!loading && cuentas.length > 0" class="data-table">

          <ng-container matColumnDef="numeroCuenta">
            <th mat-header-cell *matHeaderCellDef>Número de cuenta</th>
            <td mat-cell *matCellDef="let row">
              <span class="numero-badge">{{ row.numeroCuenta }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="tipo">
            <th mat-header-cell *matHeaderCellDef>Tipo</th>
            <td mat-cell *matCellDef="let row">
              <span class="tipo-chip" [ngClass]="'tipo-' + row.tipo.toLowerCase()">
                {{ getTipoLabel(row.tipo) }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="descripcion">
            <th mat-header-cell *matHeaderCellDef>Descripción</th>
            <td mat-cell *matCellDef="let row">{{ row.descripcion ?? '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="activo">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let row">
              <span class="activo-chip" [ngClass]="row.activo ? 'activo' : 'inactivo'">
                {{ row.activo ? 'Activa' : 'Inactiva' }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="gastos">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-stroked-button class="gastos-btn"
                      [routerLink]="['/cuentas', row.id, 'gastos-bancarios']">
                <mat-icon>account_balance</mat-icon>
                Gastos bancarios
              </button>
            </td>
          </ng-container>

          <ng-container matColumnDef="acciones">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button
                      [disabled]="accionando === row.id"
                      (click)="toggleEstado(row)"
                      [title]="row.activo ? 'Desactivar' : 'Activar'"
                      [class]="row.activo ? 'btn-desactivar' : 'btn-activar'">
                <mat-icon>{{ row.activo ? 'toggle_on' : 'toggle_off' }}</mat-icon>
              </button>
              <button mat-icon-button class="btn-eliminar"
                      [disabled]="accionando === row.id"
                      (click)="eliminarCuenta(row)"
                      title="Eliminar cuenta">
                <mat-icon>delete_outline</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns;" class="data-row"></tr>
        </table>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 900px; }

    .page-header { margin-bottom: 24px; }

    .back-btn { color: #6b7a8d; gap: 4px; margin-bottom: 8px; padding: 0; }

    .page-title { font-size: 26px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 14px; color: #6b7a8d; margin: 0; }

    .form-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      margin-bottom: 24px;
    }

    mat-card-title { font-size: 15px !important; font-weight: 600 !important; color: #1a2332 !important; }

    .cuenta-form {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
      margin-top: 16px;
    }

    .field-numero { flex: 1; min-width: 200px; }
    .field-tipo { width: 160px; }
    .field-desc { flex: 1; min-width: 200px; }

    .save-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 56px;
      gap: 6px;
      margin-top: 4px;
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
    }

    .table-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      overflow: hidden;
    }

    .loading-container { display: flex; justify-content: center; padding: 60px; }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px; color: #6b7a8d; gap: 12px;
    }

    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: #b0bec5; }

    .data-table { width: 100%; }

    .mat-mdc-header-row { background: #f8fafc; }

    .mat-mdc-header-cell {
      font-size: 12px !important; font-weight: 600 !important;
      color: #6b7a8d !important; text-transform: uppercase; letter-spacing: 0.5px;
    }

    .mat-mdc-cell { font-size: 14px; color: #1a2332; padding: 14px 16px !important; }

    .data-row:hover { background: #f8fafc; }

    .numero-badge {
      background: #e8f0fe; color: #1a2332;
      padding: 4px 10px; border-radius: 6px; font-weight: 600; font-size: 13px;
    }

    .tipo-chip {
      padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500;
    }

    .tipo-corriente  { background: #dbeafe; color: #1e40af; }
    .tipo-ahorro     { background: #dcfce7; color: #166534; }
    .tipo-fiduciaria { background: #fef3c7; color: #92400e; }
    .tipo-otra       { background: #f3f4f6; color: #374151; }

    .activo-chip { padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500; }
    .activo   { background: #dcfce7; color: #166534; }
    .inactivo { background: #fee2e2; color: #991b1b; }

    .gastos-btn {
      border-color: #7c3aed !important;
      color: #7c3aed !important;
      border-radius: 8px !important;
      height: 34px;
      font-size: 13px;
      gap: 4px;
    }

    .btn-activar   { color: #16a34a !important; }
    .btn-desactivar { color: #d97706 !important; }
    .btn-eliminar  { color: #dc2626 !important; }
  `]
})
export class CuentasComponent implements OnInit {
  cuentas: Cuenta[] = [];
  banco: Banco | null = null;
  loading = true;
  saving = false;
  errorCrear = '';
  accionando: number | null = null;
  get columns(): string[] {
    const base = ['numeroCuenta', 'tipo', 'descripcion', 'activo', 'gastos'];
    return this.canCreate() ? [...base, 'acciones'] : base;
  }
  cuentaForm: FormGroup;
  private idBanco!: number;

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private fb: FormBuilder,
    private route: ActivatedRoute
  ) {
    this.cuentaForm = this.fb.group({
      numeroCuenta: ['', [Validators.required, Validators.maxLength(50)]],
      tipo: ['CORRIENTE', Validators.required],
      descripcion: ['', Validators.maxLength(200)]
    });
  }

  ngOnInit(): void {
    this.idBanco = Number(this.route.snapshot.paramMap.get('idBanco'));
    // Cargar banco para mostrar nombre
    this.api.listarBancos().subscribe({
      next: res => { this.banco = res.data.find(b => b.id === this.idBanco) ?? null; }
    });
    this.cargarCuentas();
  }

  cargarCuentas(): void {
    this.loading = true;
    this.api.listarCuentasPorBanco(this.idBanco).subscribe({
      next: res => { this.cuentas = res.data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  crearCuenta(): void {
    if (this.cuentaForm.invalid) return;
    this.saving = true;
    this.errorCrear = '';
    const { numeroCuenta, tipo, descripcion } = this.cuentaForm.value;
    this.api.crearCuenta(this.idBanco, numeroCuenta, tipo as TipoCuenta, descripcion ?? '').subscribe({
      next: res => {
        this.cuentas = [...this.cuentas, res.data];
        this.cuentaForm.reset({ tipo: 'CORRIENTE' });
        this.saving = false;
      },
      error: err => {
        this.saving = false;
        this.errorCrear = err.error?.message ?? 'Error al crear la cuenta';
      }
    });
  }

  toggleEstado(cuenta: Cuenta): void {
    this.accionando = cuenta.id;
    this.api.cambiarEstadoCuenta(cuenta.id, !cuenta.activo).subscribe({
      next: res => {
        this.cuentas = this.cuentas.map(c => c.id === cuenta.id ? res.data : c);
        this.accionando = null;
      },
      error: () => { this.accionando = null; }
    });
  }

  eliminarCuenta(cuenta: Cuenta): void {
    if (!confirm(`¿Eliminar la cuenta "${cuenta.numeroCuenta}"? Esta acción no se puede deshacer.`)) return;
    this.accionando = cuenta.id;
    this.api.eliminarCuenta(cuenta.id).subscribe({
      next: () => {
        this.cuentas = this.cuentas.filter(c => c.id !== cuenta.id);
        this.accionando = null;
      },
      error: () => { this.accionando = null; }
    });
  }

  getTipoLabel(tipo: TipoCuenta): string {
    const map: Record<TipoCuenta, string> = {
      CORRIENTE: 'Corriente', AHORRO: 'Ahorro',
      FIDUCIARIA: 'Fiduciaria', OTRA: 'Otra'
    };
    return map[tipo] ?? tipo;
  }

  canCreate(): boolean {
    return this.auth.hasRole('CONTADOR', 'ADMIN');
  }
}
