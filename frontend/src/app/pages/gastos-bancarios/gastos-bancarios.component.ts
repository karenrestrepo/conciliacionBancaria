import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatListModule } from '@angular/material/list';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { GastoBancario, Cuenta } from '../../core/models';

@Component({
  selector: 'app-gastos-bancarios',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule,
    MatCardModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatInputModule,
    MatProgressSpinnerModule, MatListModule, MatChipsModule, MatDividerModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <button mat-icon-button class="back-btn" [routerLink]="backLink">
          <mat-icon>arrow_back</mat-icon>
        </button>
        <div>
          <h1 class="page-title">Gastos bancarios</h1>
          <p class="page-subtitle" *ngIf="cuenta">
            {{ cuenta.numeroCuenta }} — {{ cuenta.descripcion ?? cuenta.tipo }}
          </p>
        </div>
      </div>

      <!-- Explicación -->
      <mat-card class="info-card">
        <mat-card-content>
          <div class="info-row">
            <mat-icon class="info-icon">info_outline</mat-icon>
            <p class="info-text">
              Configura las descripciones de gastos bancarios que aparecen repetidamente
              en el extracto (4x1000, comisiones, IVA sobre comisiones, etc.). Cuando
              subas el extracto, todos los movimientos cuya descripción coincida serán
              <strong>sumados en un único movimiento</strong>, que luego se concilia contra
              la nota bancaria del auxiliar contable.
            </p>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Formulario agregar (solo CONTADOR / ADMIN) -->
      <mat-card class="form-card" *ngIf="canEdit()">
        <mat-card-header>
          <mat-card-title>Agregar descripción</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="agregar()" class="add-form">
            <mat-form-field appearance="outline" class="field-desc">
              <mat-label>Texto de descripción</mat-label>
              <input matInput formControlName="descripcion"
                     placeholder="Ej: GRAVAMEN 4X1000">
              <mat-hint>La búsqueda es parcial y no distingue mayúsculas</mat-hint>
              <mat-error *ngIf="form.get('descripcion')?.hasError('required')">
                La descripción es obligatoria
              </mat-error>
              <mat-error *ngIf="form.get('descripcion')?.hasError('maxlength')">
                Máximo 300 caracteres
              </mat-error>
            </mat-form-field>

            <button mat-flat-button class="save-btn" type="submit"
                    [disabled]="form.invalid || saving">
              <mat-spinner diameter="18" *ngIf="saving"></mat-spinner>
              <mat-icon *ngIf="!saving">add</mat-icon>
              <span>{{ saving ? 'Guardando...' : 'Agregar' }}</span>
            </button>
          </form>

          <div class="error-msg" *ngIf="errorAgregar">
            <mat-icon>error_outline</mat-icon>{{ errorAgregar }}
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Lista de descripciones configuradas -->
      <mat-card class="list-card">
        <mat-card-header>
          <mat-card-title>Descripciones configuradas</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loading" class="loading-center">
            <mat-spinner diameter="32"></mat-spinner>
          </div>

          <div *ngIf="!loading && gastos.length === 0" class="empty-state">
            <mat-icon>tag</mat-icon>
            <p>No hay descripciones configuradas</p>
            <span *ngIf="canEdit()">Agrega la primera descripción para activar la agrupación</span>
          </div>

          <div *ngIf="!loading && gastos.length > 0" class="chips-container">
            <div *ngFor="let g of gastos" class="chip-row">
              <span class="desc-chip">
                <mat-icon class="chip-icon">label_outline</mat-icon>
                {{ g.descripcion }}
              </span>
              <button mat-icon-button class="delete-btn"
                      *ngIf="canEdit()"
                      [disabled]="eliminando === g.id"
                      (click)="eliminar(g)"
                      title="Eliminar">
                <mat-spinner diameter="16" *ngIf="eliminando === g.id"></mat-spinner>
                <mat-icon *ngIf="eliminando !== g.id">close</mat-icon>
              </button>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      <!-- Resumen -->
      <mat-card class="summary-card" *ngIf="!loading && gastos.length > 0">
        <mat-card-content>
          <div class="summary-row">
            <mat-icon class="summary-icon">account_balance</mat-icon>
            <span>
              Con esta configuración, al subir el extracto se agruparán todos los
              movimientos que coincidan con <strong>{{ gastos.length }}
              {{ gastos.length === 1 ? 'descripción' : 'descripciones' }}</strong>
              en un único movimiento de tipo <strong>GASTOS BANCARIOS AGRUPADOS</strong>.
            </span>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 760px; }

    .page-header {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 24px;
    }

    .back-btn { color: #6b7a8d; }
    .page-title { font-size: 24px; font-weight: 600; color: #1a2332; margin: 0 0 2px; }
    .page-subtitle { font-size: 13px; color: #6b7a8d; margin: 0; }

    .info-card, .form-card, .list-card, .summary-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      margin-bottom: 20px;
    }

    .info-card { background: #eff6ff !important; border: 1px solid #bfdbfe !important; }

    .info-row {
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }

    .info-icon { color: #3b82f6; margin-top: 2px; flex-shrink: 0; }
    .info-text { margin: 0; font-size: 14px; color: #1e3a5f; line-height: 1.6; }

    mat-card-title { font-size: 15px !important; font-weight: 600 !important; color: #1a2332 !important; }

    .add-form {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
      margin-top: 12px;
    }

    .field-desc { flex: 1; min-width: 280px; }

    .save-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 56px;
      gap: 6px;
      margin-top: 4px;
    }

    .error-msg {
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

    .loading-center { display: flex; justify-content: center; padding: 32px; }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 40px;
      color: #6b7a8d;
      text-align: center;
    }

    .empty-state mat-icon { font-size: 40px; width: 40px; height: 40px; color: #b0bec5; }
    .empty-state span { font-size: 13px; color: #94a3b8; }

    .chips-container { display: flex; flex-direction: column; gap: 8px; padding: 8px 0; }

    .chip-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
    }

    .desc-chip {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 500;
      color: #1a2332;
      font-family: monospace;
    }

    .chip-icon { font-size: 16px; width: 16px; height: 16px; color: #64748b; }

    .delete-btn { color: #dc2626 !important; width: 32px; height: 32px; }

    .summary-card { background: #f0fdf4 !important; border: 1px solid #bbf7d0 !important; }

    .summary-row {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      font-size: 14px;
      color: #14532d;
      line-height: 1.6;
    }

    .summary-icon { color: #16a34a; flex-shrink: 0; margin-top: 2px; }
  `]
})
export class GastosBancariosComponent implements OnInit {
  gastos: GastoBancario[] = [];
  cuenta: Cuenta | null = null;
  loading = true;
  saving = false;
  eliminando: number | null = null;
  errorAgregar = '';
  idCuenta = 0;
  backLink = '/bancos';
  form: FormGroup;

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    public auth: AuthService,
    private fb: FormBuilder
  ) {
    this.form = this.fb.group({
      descripcion: ['', [Validators.required, Validators.maxLength(300)]]
    });
  }

  ngOnInit(): void {
    this.idCuenta = Number(this.route.snapshot.paramMap.get('idCuenta'));
    this.cargar();
    this.cargarCuenta();
  }

  cargar(): void {
    this.loading = true;
    this.api.listarGastosBancarios(this.idCuenta).subscribe({
      next: res => { this.gastos = res.data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  cargarCuenta(): void {
    this.api.listarTodasCuentas().subscribe({
      next: res => {
        const c = res.data.find(x => x.id === this.idCuenta) ?? null;
        this.cuenta = c;
        if (c) this.backLink = `/bancos/${c.idBanco}/cuentas`;
      }
    });
  }

  agregar(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.errorAgregar = '';
    const { descripcion } = this.form.value;
    this.api.agregarGastoBancario(this.idCuenta, descripcion).subscribe({
      next: res => {
        this.gastos = [...this.gastos, res.data];
        this.form.reset();
        this.saving = false;
      },
      error: err => {
        this.saving = false;
        this.errorAgregar = err.error?.message ?? 'Error al agregar la descripción';
      }
    });
  }

  eliminar(gasto: GastoBancario): void {
    if (!confirm(`¿Eliminar la descripción "${gasto.descripcion}"?`)) return;
    this.eliminando = gasto.id;
    this.api.eliminarGastoBancario(this.idCuenta, gasto.id).subscribe({
      next: () => {
        this.gastos = this.gastos.filter(g => g.id !== gasto.id);
        this.eliminando = null;
      },
      error: () => { this.eliminando = null; }
    });
  }

  canEdit(): boolean {
    return this.auth.hasRole('CONTADOR', 'ADMIN');
  }
}
