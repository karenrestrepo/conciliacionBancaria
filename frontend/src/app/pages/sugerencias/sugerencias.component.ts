import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Conciliacion, Sugerencia } from '../../core/models';

@Component({
  selector: 'app-sugerencias',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatCardModule, MatTableModule,
    MatIconModule, MatButtonModule, MatChipsModule,
    MatProgressSpinnerModule, MatTooltipModule, MatDividerModule,
    MatSnackBarModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Sugerencias de Conciliación</h1>
          <p class="page-subtitle" *ngIf="conciliacion">
            Período {{ conciliacion.periodo }} —
            <span [ngClass]="getEstadoClass(conciliacion.estado)">
              {{ getEstadoLabel(conciliacion.estado) }}
            </span>
          </p>
        </div>
        <div class="header-actions">
          <button mat-stroked-button routerLink="/conciliaciones" class="back-btn">
            <mat-icon>arrow_back</mat-icon>
            Volver
          </button>
          <button mat-flat-button class="revision-btn"
                  *ngIf="conciliacion?.estado === 'BORRADOR' && canReview()"
                  (click)="pasarARevision()">
            <mat-icon>rate_review</mat-icon>
            Pasar a Revisión
          </button>
          <button mat-stroked-button class="partidas-btn"
                  *ngIf="conciliacion?.estado === 'EN_REVISION'"
                  [routerLink]="['/partidas', idConciliacion]">
            <mat-icon>list_alt</mat-icon>
            Ver Partidas
          </button>
          <button mat-flat-button class="cerrar-btn"
                  *ngIf="conciliacion?.estado === 'EN_REVISION' && canClose()"
                  (click)="cerrar()">
            <mat-icon>lock</mat-icon>
            Cerrar Conciliación
          </button>
        </div>
      </div>

      <div *ngIf="loading" class="loading-container">
        <mat-spinner diameter="36"></mat-spinner>
      </div>

      <ng-container *ngIf="!loading">
        <!-- Resumen -->
        <div class="summary-grid" *ngIf="sugerencias.length > 0">
          <mat-card class="summary-card">
            <div class="summary-icon blue"><mat-icon>psychology</mat-icon></div>
            <div>
              <span class="summary-value">{{ sugerencias.length }}</span>
              <span class="summary-label">Total sugerencias</span>
            </div>
          </mat-card>
          <mat-card class="summary-card">
            <div class="summary-icon amber"><mat-icon>pending</mat-icon></div>
            <div>
              <span class="summary-value">{{ getPendientes() }}</span>
              <span class="summary-label">Pendientes</span>
            </div>
          </mat-card>
          <mat-card class="summary-card">
            <div class="summary-icon green"><mat-icon>check_circle</mat-icon></div>
            <div>
              <span class="summary-value">{{ getAceptadas() }}</span>
              <span class="summary-label">Aceptadas</span>
            </div>
          </mat-card>
          <mat-card class="summary-card">
            <div class="summary-icon red"><mat-icon>cancel</mat-icon></div>
            <div>
              <span class="summary-value">{{ getRechazadas() }}</span>
              <span class="summary-label">Rechazadas</span>
            </div>
          </mat-card>
        </div>

        <!-- Tabla -->
        <mat-card class="table-card">
          <div *ngIf="sugerencias.length === 0" class="empty-state">
            <mat-icon>psychology</mat-icon>
            <p>No hay sugerencias para esta conciliación</p>
            <p class="empty-hint">Asegúrese de haber cargado el extracto bancario y el libro auxiliar</p>
          </div>

          <table mat-table [dataSource]="sugerencias"
                 *ngIf="sugerencias.length > 0" class="data-table">

            <ng-container matColumnDef="confianza">
              <th mat-header-cell *matHeaderCellDef>Confianza</th>
              <td mat-cell *matCellDef="let row">
                <div class="confianza-wrap">
                  <div class="confianza-bar">
                    <div class="confianza-fill"
                         [style.width.%]="row.confianza * 100"
                         [ngClass]="getConfianzaClass(row.confianza)">
                    </div>
                  </div>
                  <span class="confianza-pct">{{ row.confianza * 100 | number:'1.0-0' }}%</span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="criterio">
              <th mat-header-cell *matHeaderCellDef>Criterio</th>
              <td mat-cell *matCellDef="let row">
                <span class="criterio-badge" [ngClass]="getCriterioClass(row.criterio)">
                  {{ getCriterioLabel(row.criterio) }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="movBancario">
              <th mat-header-cell *matHeaderCellDef>Movimiento Bancario</th>
              <td mat-cell *matCellDef="let row">
                <div class="mov-info">
                  <span class="mov-fecha">{{ row.fechaBancario | date:'dd/MM/yyyy' }}</span>
                  <span class="mov-desc">{{ row.descripcionBancario }}</span>
                  <span class="mov-monto" [ngClass]="getTipoClass(row.tipoBancario)">
                    {{ row.tipoBancario === 'DEBITO' ? '-' : '+' }}
                    {{ row.montoBancario | currency:'COP':'symbol':'1.0-2' }}
                  </span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="movContable">
              <th mat-header-cell *matHeaderCellDef>Movimiento Contable</th>
              <td mat-cell *matCellDef="let row">
                <div class="mov-info">
                  <span class="mov-fecha">{{ row.fechaContable | date:'dd/MM/yyyy' }}</span>
                  <span class="mov-desc">{{ row.descripcionContable }}</span>
                  <span class="mov-monto" [ngClass]="getTipoClass(row.tipoContable)">
                    {{ row.tipoContable === 'DEBITO' ? '-' : '+' }}
                    {{ row.montoContable | currency:'COP':'symbol':'1.0-2' }}
                  </span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="estado">
              <th mat-header-cell *matHeaderCellDef>Estado</th>
              <td mat-cell *matCellDef="let row">
                <span class="estado-chip" [ngClass]="getSugerenciaEstadoClass(row.estado)">
                  {{ getSugerenciaEstadoLabel(row.estado) }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="acciones">
              <th mat-header-cell *matHeaderCellDef>Acciones</th>
              <td mat-cell *matCellDef="let row">
                <div class="acciones-wrap" *ngIf="row.estado === 'PENDIENTE_REVISION' && canReview()">
                  <button mat-icon-button class="accept-btn"
                          matTooltip="Aceptar sugerencia"
                          (click)="aceptar(row)">
                    <mat-icon>check_circle</mat-icon>
                  </button>
                  <button mat-icon-button class="reject-btn"
                          matTooltip="Rechazar sugerencia"
                          (click)="rechazar(row)">
                    <mat-icon>cancel</mat-icon>
                  </button>
                </div>
                <span *ngIf="row.estado !== 'PENDIENTE_REVISION'" class="no-action">—</span>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;"
                class="data-row" [ngClass]="getRowClass(row.estado)"></tr>
          </table>
        </mat-card>
      </ng-container>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 1400px; }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 32px;
    }

    .page-title { font-size: 26px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 14px; color: #6b7a8d; margin: 0; }

    .header-actions { display: flex; gap: 12px; align-items: center; }

    .back-btn { color: #6b7a8d; border-color: #d1d5db !important; border-radius: 8px !important; }

    .revision-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 42px;
      gap: 6px;
    }
    .partidas-btn {
      border-color: #1a2332 !important;
      color: #1a2332 !important;
      border-radius: 8px !important;
      height: 42px;
      gap: 6px;
    }
    .cerrar-btn {
      background: #c0392b !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 42px;
      gap: 6px;
    }

    .loading-container { display: flex; justify-content: center; padding: 60px; }

    .summary-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 16px;
      margin-bottom: 24px;
    }

    .summary-card {
      display: flex !important;
      flex-direction: row !important;
      align-items: center;
      gap: 14px;
      padding: 16px 20px !important;
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
    }

    .summary-icon {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .summary-icon mat-icon { color: #fff; font-size: 20px; }
    .blue { background: #3d7ebf; }
    .amber { background: #f59e0b; }
    .green { background: #22c55e; }
    .red { background: #e53935; }

    .summary-value { display: block; font-size: 24px; font-weight: 700; color: #1a2332; }
    .summary-label { display: block; font-size: 12px; color: #6b7a8d; }

    .table-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      overflow: hidden;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 60px;
      color: #6b7a8d;
    }

    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: #b0bec5; margin-bottom: 12px; }
    .empty-hint { font-size: 13px; color: #9ca3af; margin: 4px 0 0; }

    .data-table { width: 100%; }
    .mat-mdc-header-row { background: #f8fafc; }
    .mat-mdc-header-cell {
      font-size: 12px !important;
      font-weight: 600 !important;
      color: #6b7a8d !important;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .mat-mdc-cell { padding: 12px 16px !important; vertical-align: middle; }

    .data-row:hover { background: #f8fafc; }
    .row-aceptada { background: #f0fdf4; }
    .row-rechazada { background: #fef2f2; }

    .confianza-wrap { display: flex; align-items: center; gap: 8px; min-width: 100px; }
    .confianza-bar { flex: 1; height: 6px; background: #e5e7eb; border-radius: 3px; overflow: hidden; }
    .confianza-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
    .conf-high { background: #22c55e; }
    .conf-mid { background: #f59e0b; }
    .conf-low { background: #e53935; }
    .confianza-pct { font-size: 12px; font-weight: 600; color: #1a2332; white-space: nowrap; }

    .criterio-badge {
      padding: 3px 10px;
      border-radius: 20px;
      font-size: 11px;
      font-weight: 500;
      white-space: nowrap;
    }

    .crit-exacto { background: #dbeafe; color: #1e40af; }
    .crit-fecha { background: #e0e7ff; color: #3730a3; }
    .crit-aprox { background: #fef3c7; color: #92400e; }

    .mov-info { display: flex; flex-direction: column; gap: 2px; }
    .mov-fecha { font-size: 11px; color: #9ca3af; }
    .mov-desc { font-size: 13px; color: #1a2332; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .mov-monto { font-size: 13px; font-weight: 600; }
    .tipo-debito { color: #e53935; }
    .tipo-credito { color: #22c55e; }

    .estado-chip {
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 11px;
      font-weight: 500;
    }

    .sug-pendiente { background: #fef3c7; color: #92400e; }
    .sug-aceptada { background: #dcfce7; color: #166534; }
    .sug-rechazada { background: #fee2e2; color: #991b1b; }
    .sug-reasignada { background: #f3f4f6; color: #374151; }

    .acciones-wrap { display: flex; gap: 4px; }
    .accept-btn { color: #22c55e !important; }
    .reject-btn { color: #e53935 !important; }
    .no-action { color: #9ca3af; font-size: 14px; }

    .estado-borrador { color: #92400e; font-weight: 500; }
    .estado-revision { color: #9a3412; font-weight: 500; }
    .estado-cerrada { color: #166534; font-weight: 500; }
  `]
})
export class SugerenciasComponent implements OnInit {
  sugerencias: Sugerencia[] = [];
  conciliacion: Conciliacion | null = null;
  loading = true;
  idConciliacion = 0;
  columns = ['confianza', 'criterio', 'movBancario', 'movContable', 'estado', 'acciones'];

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.idConciliacion = +this.route.snapshot.paramMap.get('id')!;
    this.api.obtenerConciliacion(this.idConciliacion).subscribe({
      next: res => { this.conciliacion = res.data; }
    });
    this.cargarSugerencias();
  }

  cargarSugerencias(): void {
    this.loading = true;
    this.api.obtenerSugerencias(this.idConciliacion).subscribe({
      next: res => { this.sugerencias = res.data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  aceptar(sugerencia: Sugerencia): void {
    this.api.aceptarSugerencia(this.idConciliacion, sugerencia.id).subscribe({
      next: res => {
        sugerencia.estado = res.data.estado;
        this.snackBar.open('Sugerencia aceptada', 'Cerrar', { duration: 3000, panelClass: 'snack-success' });
      }
    });
  }

  rechazar(sugerencia: Sugerencia): void {
    this.api.rechazarSugerencia(this.idConciliacion, sugerencia.id).subscribe({
      next: res => {
        sugerencia.estado = res.data.estado;
        this.snackBar.open('Sugerencia rechazada', 'Cerrar', { duration: 3000 });
      }
    });
  }

  pasarARevision(): void {
    this.api.pasarARevision(this.idConciliacion).subscribe({
      next: res => {
        this.conciliacion = res.data;
        this.snackBar.open('Conciliación pasada a revisión', 'Cerrar', { duration: 3000, panelClass: 'snack-success' });
      }
    });
  }

  canReview(): boolean {
    return this.auth.hasRole('CONTADOR', 'ADMIN');
  }

  canClose(): boolean {
    return this.auth.hasRole('CONTADOR');
  }

  cerrar(): void {
    this.api.cerrarConciliacion(this.idConciliacion).subscribe({
      next: res => {
        this.conciliacion = res.data;
        this.snackBar.open('Conciliación cerrada', 'Cerrar', { duration: 3000, panelClass: 'snack-success' });
      },
      error: err => {
        this.snackBar.open(err.error?.message ?? 'Error al cerrar la conciliación', 'Cerrar', { duration: 4000 });
      }
    });
  }

  getPendientes(): number {
    return this.sugerencias.filter(s => s.estado === 'PENDIENTE_REVISION').length;
  }

  getAceptadas(): number {
    return this.sugerencias.filter(s => s.estado === 'ACEPTADA').length;
  }

  getRechazadas(): number {
    return this.sugerencias.filter(s => s.estado === 'RECHAZADA').length;
  }

  getConfianzaClass(c: number): string {
    if (c >= 0.85) return 'conf-high';
    if (c >= 0.70) return 'conf-mid';
    return 'conf-low';
  }

  getCriterioClass(criterio: string): string {
    const map: Record<string, string> = {
      'MONTO_EXACTO': 'crit-exacto',
      'MONTO_FECHA_PROXIMA': 'crit-fecha',
      'MONTO_APROXIMADO': 'crit-aprox'
    };
    return map[criterio] ?? '';
  }

  getCriterioLabel(criterio: string): string {
    const map: Record<string, string> = {
      'MONTO_EXACTO': 'Monto Exacto',
      'MONTO_FECHA_PROXIMA': 'Fecha Próxima',
      'MONTO_APROXIMADO': 'Monto Aprox.'
    };
    return map[criterio] ?? criterio;
  }

  getSugerenciaEstadoClass(estado: string): string {
    const map: Record<string, string> = {
      'PENDIENTE_REVISION': 'sug-pendiente',
      'ACEPTADA': 'sug-aceptada',
      'RECHAZADA': 'sug-rechazada',
      'REASIGNADA': 'sug-reasignada'
    };
    return map[estado] ?? '';
  }

  getSugerenciaEstadoLabel(estado: string): string {
    const map: Record<string, string> = {
      'PENDIENTE_REVISION': 'Pendiente',
      'ACEPTADA': 'Aceptada',
      'RECHAZADA': 'Rechazada',
      'REASIGNADA': 'Reasignada'
    };
    return map[estado] ?? estado;
  }

  getTipoClass(tipo: string): string {
    return tipo === 'DEBITO' ? 'tipo-debito' : 'tipo-credito';
  }

  getRowClass(estado: string): string {
    if (estado === 'ACEPTADA') return 'row-aceptada';
    if (estado === 'RECHAZADA') return 'row-rechazada';
    return '';
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
}
