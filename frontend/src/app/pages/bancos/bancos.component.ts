import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Banco } from '../../core/models';

@Component({
  selector: 'app-bancos',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, MatCardModule, MatTableModule,
    MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatProgressSpinnerModule, MatDividerModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Bancos</h1>
          <p class="page-subtitle">Gestión de entidades bancarias para conciliaciones</p>
        </div>
      </div>

      <!-- Formulario crear banco (solo CONTADOR / ADMIN) -->
      <mat-card class="form-card" *ngIf="canCreate()">
        <mat-card-header>
          <mat-card-title>Agregar banco</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="bancoForm" (ngSubmit)="crearBanco()" class="banco-form">
            <mat-form-field appearance="outline" class="field-nombre">
              <mat-label>Nombre del banco</mat-label>
              <input matInput formControlName="nombre" placeholder="Bancolombia">
              <mat-error *ngIf="bancoForm.get('nombre')?.hasError('required')">
                El nombre es obligatorio
              </mat-error>
              <mat-error *ngIf="bancoForm.get('nombre')?.hasError('maxlength')">
                Máximo 150 caracteres
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="field-codigo">
              <mat-label>Código (opcional)</mat-label>
              <input matInput formControlName="codigo" placeholder="BC001">
            </mat-form-field>

            <button mat-flat-button class="save-btn" type="submit"
                    [disabled]="bancoForm.invalid || saving">
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

      <!-- Tabla de bancos -->
      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-container">
          <mat-spinner diameter="36"></mat-spinner>
        </div>

        <div *ngIf="!loading && bancos.length === 0" class="empty-state">
          <mat-icon>account_balance_wallet</mat-icon>
          <p>No hay bancos registrados</p>
        </div>

        <table mat-table [dataSource]="bancos"
               *ngIf="!loading && bancos.length > 0" class="data-table">

          <ng-container matColumnDef="nombre">
            <th mat-header-cell *matHeaderCellDef>Nombre</th>
            <td mat-cell *matCellDef="let row">
              <span class="banco-nombre">{{ row.nombre }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="codigo">
            <th mat-header-cell *matHeaderCellDef>Código</th>
            <td mat-cell *matCellDef="let row">{{ row.codigo ?? '—' }}</td>
          </ng-container>

          <ng-container matColumnDef="activo">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let row">
              <span class="activo-chip" [ngClass]="row.activo ? 'activo' : 'inactivo'">
                {{ row.activo ? 'Activo' : 'Inactivo' }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="tsCreacion">
            <th mat-header-cell *matHeaderCellDef>Fecha registro</th>
            <td mat-cell *matCellDef="let row">
              {{ row.tsCreacion | date:'dd/MM/yyyy' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="cuentas">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-stroked-button class="cuentas-btn"
                      [routerLink]="['/bancos', row.id, 'cuentas']">
                <mat-icon>credit_card</mat-icon>
                Cuentas
              </button>
            </td>
          </ng-container>

          <ng-container matColumnDef="configExtracto">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-stroked-button class="config-btn"
                      [routerLink]="['/configuracion-extracto', row.id]"
                      *ngIf="canCreate()">
                <mat-icon>settings</mat-icon>
                Configurar extractos
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

    .page-header {
      margin-bottom: 24px;
    }

    .page-title { font-size: 26px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 14px; color: #6b7a8d; margin: 0; }

    .form-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      margin-bottom: 24px;
    }

    mat-card-title { font-size: 15px !important; font-weight: 600 !important; color: #1a2332 !important; }

    .banco-form {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
      margin-top: 16px;
    }

    .field-nombre { flex: 1; min-width: 220px; }
    .field-codigo { width: 160px; }

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

    .loading-container {
      display: flex;
      justify-content: center;
      padding: 60px;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 60px;
      color: #6b7a8d;
      gap: 12px;
    }

    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: #b0bec5; }

    .data-table { width: 100%; }

    .mat-mdc-header-row { background: #f8fafc; }

    .mat-mdc-header-cell {
      font-size: 12px !important;
      font-weight: 600 !important;
      color: #6b7a8d !important;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .mat-mdc-cell {
      font-size: 14px;
      color: #1a2332;
      padding: 14px 16px !important;
    }

    .data-row:hover { background: #f8fafc; }

    .banco-nombre { font-weight: 500; }

    .activo-chip {
      padding: 3px 10px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
    }

    .activo { background: #dcfce7; color: #166534; }
    .inactivo { background: #fee2e2; color: #991b1b; }

    .cuentas-btn {
      border-color: #3d7ebf !important;
      color: #3d7ebf !important;
      border-radius: 8px !important;
      height: 34px;
      font-size: 13px;
      gap: 4px;
    }

    .config-btn {
      border-color: #6b7a8d !important;
      color: #6b7a8d !important;
      border-radius: 8px !important;
      height: 34px;
      font-size: 13px;
      gap: 4px;
    }
  `]
})
export class BancosComponent implements OnInit {
  bancos: Banco[] = [];
  loading = true;
  saving = false;
  errorCrear = '';
  columns = ['nombre', 'codigo', 'activo', 'tsCreacion', 'cuentas', 'configExtracto'];
  bancoForm: FormGroup;

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private fb: FormBuilder
  ) {
    this.bancoForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(150)]],
      codigo: ['', Validators.maxLength(20)]
    });
  }

  ngOnInit(): void {
    this.cargarBancos();
  }

  cargarBancos(): void {
    this.loading = true;
    this.api.listarBancos().subscribe({
      next: res => { this.bancos = res.data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  crearBanco(): void {
    if (this.bancoForm.invalid) return;
    this.saving = true;
    this.errorCrear = '';
    const { nombre, codigo } = this.bancoForm.value;
    this.api.crearBanco(nombre, codigo ?? '').subscribe({
      next: res => {
        this.bancos = [...this.bancos, res.data];
        this.bancoForm.reset();
        this.saving = false;
      },
      error: err => {
        this.saving = false;
        this.errorCrear = err.error?.message ?? 'Error al crear el banco';
      }
    });
  }

  canCreate(): boolean {
    return this.auth.hasRole('CONTADOR', 'ADMIN');
  }
}
