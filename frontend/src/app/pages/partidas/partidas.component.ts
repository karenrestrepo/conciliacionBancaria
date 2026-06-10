import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-partidas',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, MatCardModule,
    MatTableModule, MatIconModule, MatButtonModule, MatChipsModule,
    MatFormFieldModule, MatInputModule, MatDatepickerModule,
    MatNativeDateModule, MatSnackBarModule, MatProgressSpinnerModule,
    MatDividerModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Partidas No Conciliadas</h1>
          <p class="page-subtitle">Conciliación #{{ idConciliacion }} — Justifique, arrastre o elimine cada partida para poder cerrar</p>
        </div>
        <button mat-stroked-button [routerLink]="['/sugerencias', idConciliacion]" class="back-btn">
          <mat-icon>arrow_back</mat-icon>
          Volver a Sugerencias
        </button>
      </div>

      <div *ngIf="loading" class="loading-container">
        <mat-spinner diameter="36"></mat-spinner>
      </div>

      <ng-container *ngIf="!loading">
        <div class="summary-row">
          <span class="count-badge pending">{{ getPendientes() }} pendientes</span>
          <span class="count-badge justified">{{ getJustificadas() }} justificadas</span>
          <span class="count-badge dragged" *ngIf="getArrastradas() > 0">{{ getArrastradas() }} arrastradas</span>
          <span class="count-badge historical" *ngIf="partidasHistoricas.length > 0">
            {{ partidasHistoricas.length }} de meses anteriores
          </span>
        </div>

        <!-- ── Partidas del período actual ──────────────────────────────────── -->
        <div *ngFor="let partida of partidas" class="partida-card">
          <mat-card>
            <mat-card-content>
              <div class="partida-header">
                <div class="partida-meta">
                  <span class="origen-badge" [class.bancario]="partida.tipoOrigen === 'BANCARIO'"
                                              [class.contable]="partida.tipoOrigen === 'CONTABLE'">
                    <mat-icon>{{ partida.tipoOrigen === 'BANCARIO' ? 'account_balance' : 'book' }}</mat-icon>
                    {{ partida.tipoOrigen === 'BANCARIO' ? 'Movimiento Bancario' : 'Movimiento Contable' }}
                  </span>
                  <span class="partida-id">Partida #{{ partida.id }}</span>
                </div>
                <span class="estado-badge"
                      [class.pendiente]="partida.estado === 'PENDIENTE'"
                      [class.justificada]="partida.estado === 'JUSTIFICADA'"
                      [class.arrastrada]="partida.estado === 'ARRASTRADA'">
                  {{ getEstadoLabel(partida.estado) }}
                </span>
              </div>

              <!-- Detalles del movimiento -->
              <div class="mov-detalle" *ngIf="partida.fechaMovimiento || partida.montoMovimiento">
                <div class="mov-detalle-item">
                  <mat-icon>calendar_today</mat-icon>
                  <span>{{ partida.fechaMovimiento | date:'dd/MM/yyyy' }}</span>
                </div>
                <div class="mov-detalle-item" *ngIf="partida.descripcionMovimiento">
                  <mat-icon>description</mat-icon>
                  <span class="mov-desc-text">{{ partida.descripcionMovimiento }}</span>
                </div>
                <div class="mov-detalle-item">
                  <mat-icon>attach_money</mat-icon>
                  <span class="mov-monto-text" [class.debito]="partida.tipoMovimiento === 'DEBITO'"
                                               [class.credito]="partida.tipoMovimiento === 'CREDITO'">
                    {{ partida.tipoMovimiento === 'DEBITO' ? '−' : '+' }}
                    {{ partida.montoMovimiento | currency:'COP':'symbol':'1.0-2' }}
                  </span>
                </div>
              </div>

              <!-- Arrastrada: mostrar período destino -->
              <div *ngIf="partida.estado === 'ARRASTRADA'" class="arrastrada-info">
                <mat-icon>forward</mat-icon>
                <span>Marcada para el período <strong>{{ partida.periodoArrastre }}</strong></span>
              </div>

              <!-- Justificada: mostrar justificación -->
              <div *ngIf="partida.estado === 'JUSTIFICADA'" class="justificacion-actual">
                <mat-icon>check_circle</mat-icon>
                <div>
                  <p class="just-texto">{{ partida.justificacion }}</p>
                  <p class="just-fecha">{{ partida.fechaJustificacion | date:'dd/MM/yyyy' }}</p>
                </div>
              </div>

              <!-- Pendiente: formulario de acción -->
              <div *ngIf="partida.estado === 'PENDIENTE'" class="acciones-pendiente">
                <!-- Tab de justificación -->
                <div class="accion-tabs">
                  <button class="tab-btn" [class.active]="getTab(partida.id) === 'justificar'"
                          (click)="setTab(partida.id, 'justificar')">
                    <mat-icon>edit_note</mat-icon> Justificar
                  </button>
                  <button class="tab-btn" [class.active]="getTab(partida.id) === 'arrastrar'"
                          (click)="setTab(partida.id, 'arrastrar')">
                    <mat-icon>forward</mat-icon> Próximo mes
                  </button>
                </div>

                <form *ngIf="getTab(partida.id) === 'justificar'"
                      [formGroup]="getForm(partida.id)"
                      (ngSubmit)="justificar(partida)"
                      class="justificar-form">
                  <mat-form-field appearance="outline" class="field-texto">
                    <mat-label>Justificación</mat-label>
                    <textarea matInput formControlName="justificacion" rows="2"
                              placeholder="Explique por qué este movimiento no tiene par..."></textarea>
                  </mat-form-field>
                  <div class="form-row">
                    <mat-form-field appearance="outline" class="field-fecha">
                      <mat-label>Fecha</mat-label>
                      <input matInput [matDatepicker]="picker" formControlName="fecha">
                      <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
                      <mat-datepicker #picker></mat-datepicker>
                    </mat-form-field>
                    <button mat-flat-button class="guardar-btn" type="submit"
                            [disabled]="getForm(partida.id).invalid || saving[partida.id]">
                      <mat-spinner diameter="16" *ngIf="saving[partida.id]"></mat-spinner>
                      <span *ngIf="!saving[partida.id]">Guardar Justificación</span>
                    </button>
                  </div>
                </form>

                <form *ngIf="getTab(partida.id) === 'arrastrar'"
                      [formGroup]="getArrastrarForm(partida.id)"
                      (ngSubmit)="arrastrar(partida)"
                      class="justificar-form">
                  <p class="arrastrar-hint">
                    Esta partida quedará marcada como pendiente para el período indicado
                    y no bloqueará el cierre de la conciliación actual.
                  </p>
                  <div class="form-row">
                    <mat-form-field appearance="outline" class="field-periodo">
                      <mat-label>Período destino (YYYY-MM)</mat-label>
                      <input matInput formControlName="periodoDestino" placeholder="{{ siguienteMes() }}">
                      <mat-error>Formato YYYY-MM requerido</mat-error>
                    </mat-form-field>
                    <button mat-flat-button class="arrastrar-btn" type="submit"
                            [disabled]="getArrastrarForm(partida.id).invalid || saving[partida.id]">
                      <mat-spinner diameter="16" *ngIf="saving[partida.id]"></mat-spinner>
                      <span *ngIf="!saving[partida.id]">Marcar para próximo mes</span>
                    </button>
                  </div>
                </form>
              </div>
            </mat-card-content>
          </mat-card>
        </div>

        <div *ngIf="partidas.length === 0" class="empty-state">
          <mat-icon>check_circle</mat-icon>
          <p>No hay partidas para esta conciliación</p>
        </div>

        <!-- ── Partidas de meses anteriores ─────────────────────────────────── -->
        <ng-container *ngIf="partidasHistoricas.length > 0">
          <mat-divider class="section-divider"></mat-divider>
          <div class="historicas-header">
            <mat-icon>history</mat-icon>
            <div>
              <h2 class="historicas-title">Partidas de conciliaciones anteriores</h2>
              <p class="historicas-subtitle">
                Movimientos pendientes de períodos anteriores para la misma cuenta.
                Si el auxiliar actual los registra, pueden conciliarse manualmente.
              </p>
            </div>
          </div>

          <div *ngFor="let partida of partidasHistoricas" class="partida-card historica">
            <mat-card>
              <mat-card-content>
                <div class="partida-header">
                  <div class="partida-meta">
                    <span class="hist-badge">Conciliación #{{ partida.idConciliacion }}</span>
                    <span class="origen-badge" [class.bancario]="partida.tipoOrigen === 'BANCARIO'"
                                                [class.contable]="partida.tipoOrigen === 'CONTABLE'">
                      <mat-icon>{{ partida.tipoOrigen === 'BANCARIO' ? 'account_balance' : 'book' }}</mat-icon>
                      {{ partida.tipoOrigen === 'BANCARIO' ? 'Bancario' : 'Contable' }}
                    </span>
                  </div>
                  <span class="estado-badge pendiente">{{ getEstadoLabel(partida.estado) }}</span>
                </div>

                <div class="mov-detalle" *ngIf="partida.fechaMovimiento || partida.montoMovimiento">
                  <div class="mov-detalle-item">
                    <mat-icon>calendar_today</mat-icon>
                    <span>{{ partida.fechaMovimiento | date:'dd/MM/yyyy' }}</span>
                  </div>
                  <div class="mov-detalle-item" *ngIf="partida.descripcionMovimiento">
                    <mat-icon>description</mat-icon>
                    <span class="mov-desc-text">{{ partida.descripcionMovimiento }}</span>
                  </div>
                  <div class="mov-detalle-item">
                    <mat-icon>attach_money</mat-icon>
                    <span class="mov-monto-text" [class.debito]="partida.tipoMovimiento === 'DEBITO'"
                                                 [class.credito]="partida.tipoMovimiento === 'CREDITO'">
                      {{ partida.tipoMovimiento === 'DEBITO' ? '−' : '+' }}
                      {{ partida.montoMovimiento | currency:'COP':'symbol':'1.0-2' }}
                    </span>
                  </div>
                </div>
              </mat-card-content>
            </mat-card>
          </div>
        </ng-container>
      </ng-container>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 900px; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
    .page-title { font-size: 24px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 13px; color: #6b7a8d; margin: 0; }
    .back-btn { color: #6b7a8d; }
    .loading-container { display: flex; justify-content: center; padding: 40px; }

    .summary-row { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
    .count-badge { padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 500; }
    .count-badge.pending { background: #fef3c7; color: #92400e; }
    .count-badge.justified { background: #d1fae5; color: #065f46; }
    .count-badge.dragged { background: #e0e7ff; color: #3730a3; }
    .count-badge.historical { background: #f3f4f6; color: #374151; }

    .partida-card { margin-bottom: 16px; }
    .partida-card.historica mat-card { border: 1px dashed #d1d5db !important; background: #fafafa; }

    .partida-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
    .partida-meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

    .origen-badge { display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 6px; font-size: 13px; font-weight: 500; }
    .origen-badge mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .origen-badge.bancario { background: #dbeafe; color: #1e40af; }
    .origen-badge.contable { background: #ede9fe; color: #5b21b6; }

    .hist-badge { background: #f3f4f6; color: #374151; padding: 3px 10px; border-radius: 6px; font-size: 11px; font-weight: 500; }
    .partida-id { font-size: 12px; color: #9ca3af; }

    .estado-badge { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
    .estado-badge.pendiente { background: #fef3c7; color: #b45309; }
    .estado-badge.justificada { background: #d1fae5; color: #065f46; }
    .estado-badge.arrastrada { background: #e0e7ff; color: #3730a3; }

    /* Detalles del movimiento */
    .mov-detalle {
      display: flex; gap: 20px; flex-wrap: wrap; align-items: center;
      background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 8px;
      padding: 10px 14px; margin-bottom: 12px;
    }
    .mov-detalle-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; }
    .mov-detalle-item mat-icon { font-size: 15px; width: 15px; height: 15px; color: #9ca3af; }
    .mov-desc-text { max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .mov-monto-text { font-weight: 600; }
    .mov-monto-text.debito { color: #dc2626; }
    .mov-monto-text.credito { color: #16a34a; }

    .arrastrada-info {
      display: flex; align-items: center; gap: 8px;
      background: #ede9fe; border: 1px solid #c4b5fd; border-radius: 8px;
      padding: 10px 14px; color: #5b21b6; font-size: 13px;
    }
    .arrastrada-info mat-icon { color: #7c3aed; }

    .justificacion-actual { display: flex; align-items: flex-start; gap: 10px; background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 12px; }
    .justificacion-actual mat-icon { color: #16a34a; margin-top: 2px; }
    .just-texto { margin: 0 0 4px; font-size: 14px; color: #1a2332; }
    .just-fecha { margin: 0; font-size: 12px; color: #6b7a8d; }

    .acciones-pendiente { margin-top: 4px; }

    .accion-tabs { display: flex; gap: 8px; margin-bottom: 14px; }
    .tab-btn {
      display: flex; align-items: center; gap: 6px;
      padding: 6px 16px; border-radius: 8px; border: 1px solid #d1d5db;
      background: #fff; color: #6b7a8d; font-size: 13px; font-weight: 500;
      cursor: pointer; transition: all 0.15s;
    }
    .tab-btn mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .tab-btn.active { border-color: #1a2332; background: #1a2332; color: #fff; }
    .tab-btn:hover:not(.active) { border-color: #6b7a8d; }

    .justificar-form { display: flex; flex-direction: column; gap: 12px; }
    .field-texto { width: 100%; }
    .form-row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .field-fecha { width: 200px; }
    .field-periodo { width: 240px; }

    .guardar-btn { background: #1a2332 !important; color: #fff !important; border-radius: 8px !important; height: 42px; }
    .arrastrar-btn { background: #5b21b6 !important; color: #fff !important; border-radius: 8px !important; height: 42px; }

    .arrastrar-hint { font-size: 13px; color: #6b7a8d; margin: 0 0 12px; }

    .empty-state { text-align: center; padding: 60px; color: #9ca3af; }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; display: block; margin: 0 auto 12px; color: #22c55e; }

    .section-divider { margin: 32px 0 24px; }

    .historicas-header {
      display: flex; align-items: flex-start; gap: 12px; margin-bottom: 20px;
    }
    .historicas-header mat-icon { color: #6b7a8d; margin-top: 4px; font-size: 22px; }
    .historicas-title { font-size: 17px; font-weight: 600; color: #374151; margin: 0 0 4px; }
    .historicas-subtitle { font-size: 13px; color: #6b7a8d; margin: 0; }
  `]
})
export class PartidasComponent implements OnInit {
  idConciliacion!: number;
  partidas: any[] = [];
  partidasHistoricas: any[] = [];
  loading = true;
  saving: Record<number, boolean> = {};
  private forms: Record<number, FormGroup> = {};
  private arrastrarForms: Record<number, FormGroup> = {};
  private tabs: Record<number, 'justificar' | 'arrastrar'> = {};

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private fb: FormBuilder,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.idConciliacion = +this.route.snapshot.paramMap.get('id')!;
    this.cargar();
  }

  cargar(): void {
    this.loading = true;
    this.api.listarPartidas(this.idConciliacion).subscribe({
      next: res => {
        this.partidas = res.data;
        this.partidas.forEach(p => {
          if (p.estado === 'PENDIENTE') {
            this.tabs[p.id] = 'justificar';
            this.forms[p.id] = this.fb.group({
              justificacion: ['', Validators.required],
              fecha: [new Date(), Validators.required]
            });
            this.arrastrarForms[p.id] = this.fb.group({
              periodoDestino: [this.siguienteMes(), [
                Validators.required,
                Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/)
              ]]
            });
          }
        });
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
    this.api.listarPartidasHistoricas(this.idConciliacion).subscribe({
      next: res => { this.partidasHistoricas = res.data; }
    });
  }

  getForm(id: number): FormGroup { return this.forms[id]; }
  getArrastrarForm(id: number): FormGroup { return this.arrastrarForms[id]; }
  getTab(id: number): string { return this.tabs[id] ?? 'justificar'; }
  setTab(id: number, tab: 'justificar' | 'arrastrar'): void { this.tabs[id] = tab; }

  justificar(partida: any): void {
    const form = this.forms[partida.id];
    if (form.invalid) return;
    this.saving[partida.id] = true;
    const fecha = form.value.fecha instanceof Date
      ? form.value.fecha.toISOString().split('T')[0]
      : form.value.fecha;
    this.api.justificarPartida(this.idConciliacion, partida.id, form.value.justificacion, fecha).subscribe({
      next: () => {
        partida.estado = 'JUSTIFICADA';
        partida.justificacion = form.value.justificacion;
        partida.fechaJustificacion = fecha;
        this.saving[partida.id] = false;
        this.snackBar.open('Partida justificada', 'Cerrar', { duration: 3000, panelClass: 'snack-success' });
      },
      error: err => {
        this.saving[partida.id] = false;
        this.snackBar.open(err.error?.message ?? 'Error al justificar', 'Cerrar', { duration: 3000 });
      }
    });
  }

  arrastrar(partida: any): void {
    const form = this.arrastrarForms[partida.id];
    if (form.invalid) return;
    this.saving[partida.id] = true;
    const periodoDestino = form.value.periodoDestino;
    this.api.arrastrarPartida(this.idConciliacion, partida.id, periodoDestino).subscribe({
      next: res => {
        partida.estado = 'ARRASTRADA';
        partida.periodoArrastre = periodoDestino;
        this.saving[partida.id] = false;
        this.snackBar.open(`Partida marcada para el período ${periodoDestino}`, 'Cerrar',
          { duration: 3000, panelClass: 'snack-success' });
      },
      error: err => {
        this.saving[partida.id] = false;
        this.snackBar.open(err.error?.message ?? 'Error al arrastrar partida', 'Cerrar', { duration: 3000 });
      }
    });
  }

  siguienteMes(): string {
    const d = new Date();
    d.setMonth(d.getMonth() + 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  getEstadoLabel(estado: string): string {
    const map: Record<string, string> = {
      'PENDIENTE': 'Pendiente', 'JUSTIFICADA': 'Justificada', 'ARRASTRADA': 'Próximo mes'
    };
    return map[estado] ?? estado;
  }

  getPendientes(): number { return this.partidas.filter(p => p.estado === 'PENDIENTE').length; }
  getJustificadas(): number { return this.partidas.filter(p => p.estado === 'JUSTIFICADA').length; }
  getArrastradas(): number { return this.partidas.filter(p => p.estado === 'ARRASTRADA').length; }
}
