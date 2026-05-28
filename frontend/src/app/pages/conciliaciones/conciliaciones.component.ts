import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Conciliacion } from '../../core/models';

@Component({
  selector: 'app-conciliaciones',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatCardModule, MatTableModule,
    MatIconModule, MatButtonModule, MatChipsModule,
    MatProgressSpinnerModule, MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Conciliaciones</h1>
          <p class="page-subtitle">Gestión de procesos de conciliación bancaria</p>
        </div>
        <button mat-flat-button routerLink="/nueva-conciliacion"
                *ngIf="canCreate()" class="action-btn">
          <mat-icon>add</mat-icon>
          Nueva Conciliación
        </button>
      </div>

      <mat-card class="table-card">
        <div *ngIf="loading" class="loading-container">
          <mat-spinner diameter="36"></mat-spinner>
        </div>

        <div *ngIf="!loading && conciliaciones.length === 0" class="empty-state">
          <mat-icon>account_balance</mat-icon>
          <p>No hay conciliaciones registradas</p>
          <button mat-flat-button routerLink="/nueva-conciliacion"
                  *ngIf="canCreate()" class="action-btn">
            Crear primera conciliación
          </button>
        </div>

        <table mat-table [dataSource]="conciliaciones"
               *ngIf="!loading && conciliaciones.length > 0" class="data-table">

          <ng-container matColumnDef="periodo">
            <th mat-header-cell *matHeaderCellDef>Período</th>
            <td mat-cell *matCellDef="let row">
              <span class="periodo-badge">{{ row.periodo }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="nombreBanco">
            <th mat-header-cell *matHeaderCellDef>Banco / Cuenta</th>
            <td mat-cell *matCellDef="let row">
              <span class="banco-cuenta">
                <span class="banco-label">{{ row.nombreBanco ?? '—' }}</span>
                <span class="cuenta-label" *ngIf="row.numeroCuenta">{{ row.numeroCuenta }}</span>
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="estado">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let row">
              <span class="estado-chip" [ngClass]="getEstadoClass(row.estado)">
                {{ getEstadoLabel(row.estado) }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="tsCreacion">
            <th mat-header-cell *matHeaderCellDef>Fecha Creación</th>
            <td mat-cell *matCellDef="let row">
              {{ row.tsCreacion | date:'dd/MM/yyyy HH:mm' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="saldoExtracto">
            <th mat-header-cell *matHeaderCellDef>Saldo Extracto</th>
            <td mat-cell *matCellDef="let row">
              {{ row.saldoExtracto != null ? (row.saldoExtracto | currency:'COP':'symbol':'1.0-0') : '—' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="saldoAuxiliar">
            <th mat-header-cell *matHeaderCellDef>Saldo Auxiliar</th>
            <td mat-cell *matCellDef="let row">
              {{ row.saldoAuxiliar != null ? (row.saldoAuxiliar | currency:'COP':'symbol':'1.0-0') : '—' }}
            </td>
          </ng-container>

          <ng-container matColumnDef="diferencia">
            <th mat-header-cell *matHeaderCellDef>Diferencia</th>
            <td mat-cell *matCellDef="let row">
              <span [ngClass]="getDiferenciaClass(row.diferenciaSaldo)">
                {{ row.diferenciaSaldo != null ? (row.diferenciaSaldo | currency:'COP':'symbol':'1.0-0') : '—' }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="acciones">
            <th mat-header-cell *matHeaderCellDef>Acciones</th>
            <td mat-cell *matCellDef="let row">
              <button mat-icon-button
                      [routerLink]="['/sugerencias', row.id]"
                      matTooltip="Ver sugerencias"
                      *ngIf="row.estado !== 'BORRADOR'">
                <mat-icon>rate_review</mat-icon>
              </button>
              <button mat-icon-button
                      [routerLink]="['/nueva-conciliacion', row.id]"
                      matTooltip="Cargar archivos"
                      *ngIf="row.estado === 'BORRADOR' && canCreate()">
                <mat-icon>upload_file</mat-icon>
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
    .page-container { padding: 32px; max-width: 1200px; }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 32px;
    }

    .page-title {
      font-size: 26px;
      font-weight: 600;
      color: #1a2332;
      margin: 0 0 4px;
    }

    .page-subtitle { font-size: 14px; color: #6b7a8d; margin: 0; }

    .action-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 42px;
      gap: 6px;
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

    .empty-state mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      color: #b0bec5;
    }

    .data-table { width: 100%; }

    .mat-mdc-header-row { background: #f8fafc; }

    .mat-mdc-header-cell {
      font-size: 12px !important;
      font-weight: 600 !important;
      color: #6b7a8d !important;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .data-row:hover { background: #f8fafc; }

    .mat-mdc-cell {
      font-size: 14px;
      color: #1a2332;
      padding: 14px 16px !important;
    }

    .periodo-badge {
      background: #e8f0fe;
      color: #1a2332;
      padding: 4px 10px;
      border-radius: 6px;
      font-weight: 600;
      font-size: 13px;
    }

    .estado-chip {
      padding: 4px 12px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
    }

    .estado-borrador { background: #fef3c7; color: #92400e; }
    .estado-revision { background: #ffedd5; color: #9a3412; }
    .estado-cerrada { background: #dcfce7; color: #166534; }

    .diff-cero { color: #22c55e; font-weight: 600; }
    .diff-positivo { color: #f59e0b; font-weight: 600; }
    .diff-negativo { color: #e53935; font-weight: 600; }

    .banco-cuenta { display: flex; flex-direction: column; gap: 2px; }
    .banco-label { font-weight: 500; font-size: 13px; }
    .cuenta-label { font-size: 11px; color: #6b7a8d; font-family: monospace; }
  `]
})
export class ConciliacionesComponent implements OnInit {
  conciliaciones: Conciliacion[] = [];
  loading = true;
  columns = ['periodo', 'nombreBanco', 'estado', 'tsCreacion', 'saldoExtracto', 'saldoAuxiliar', 'diferencia', 'acciones'];

  constructor(private api: ApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.api.listarConciliaciones().subscribe({
      next: res => {
        this.conciliaciones = res.data;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  canCreate(): boolean {
    return this.auth.hasRole('CONTADOR', 'AUXILIAR', 'ADMIN');
  }

  getEstadoClass(estado: string): string {
    const map: Record<string, string> = {
      'BORRADOR': 'estado-borrador',
      'EN_REVISION': 'estado-revision',
      'CERRADA': 'estado-cerrada'
    };
    return map[estado] ?? '';
  }

  getEstadoLabel(estado: string): string {
    const map: Record<string, string> = {
      'BORRADOR': 'Borrador',
      'EN_REVISION': 'En Revisión',
      'CERRADA': 'Cerrada'
    };
    return map[estado] ?? estado;
  }

  getDiferenciaClass(diff: number | null): string {
    if (diff === null) return '';
    if (diff === 0) return 'diff-cero';
    if (diff > 0) return 'diff-positivo';
    return 'diff-negativo';
  }
}
