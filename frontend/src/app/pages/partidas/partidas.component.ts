import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-partidas',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, MatCardModule,
    MatIconModule, MatButtonModule, MatCheckboxModule,
    MatFormFieldModule, MatInputModule,
    MatSnackBarModule, MatProgressSpinnerModule, MatDividerModule,
    MatTooltipModule, CurrencyPipe
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Movimientos Pendientes</h1>
          <p class="page-subtitle">Conciliación #{{ idConciliacion }} — Cruce manual o dejar para el próximo mes</p>
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

        <!-- ── Resumen ──────────────────────────────────────────────────────── -->
        <div class="summary-row">
          <span class="count-badge pending">{{ getPendientes() }} pendiente(s)</span>
          <span class="count-badge incomplete" *ngIf="getIncompletos() > 0">{{ getIncompletos() }} cruce(s) incompleto(s)</span>
<span class="count-badge dragged" *ngIf="getArrastradas() > 0">{{ getArrastradas() }} próximo mes</span>
          <span class="count-badge historical" *ngIf="partidasHistoricas.length > 0">
            {{ partidasHistoricas.length }} de meses anteriores
          </span>
        </div>

        <!-- ── Panel de gastos bancarios ─────────────────────────────────── -->
        <mat-card class="gastos-card" *ngIf="totalGastosBancarios > 0">
          <mat-card-content>
            <div class="gastos-row">
              <mat-icon class="gastos-icon">account_balance</mat-icon>
              <div>
                <p class="gastos-label">Gastos bancarios a registrar en contabilidad</p>
                <p class="gastos-monto">{{ totalGastosBancarios | currency:'COP':'symbol':'1.0-0' }}</p>
              </div>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- ── Lista de partidas visibles ─────────────────────────────────── -->
        <ng-container *ngFor="let p of partidasVisibles()">
          <div class="partida-card"
               [class.incompleto]="esIncompleto(p)"
               [class.resuelta]="p.estado === 'ARRASTRADA'">
            <mat-card>
              <mat-card-content>

                <!-- Cabecera -->
                <div class="partida-header">
                  <div class="partida-meta">
                    <span class="origen-badge" [class.bancario]="p.tipoOrigen === 'BANCARIO'"
                                                [class.contable]="p.tipoOrigen === 'CONTABLE'">
                      <mat-icon>{{ p.tipoOrigen === 'BANCARIO' ? 'account_balance' : 'book' }}</mat-icon>
                      {{ p.tipoOrigen === 'BANCARIO' ? 'Movimiento Bancario' : 'Movimiento Contable' }}
                    </span>
                    <span class="partida-id">Partida #{{ p.id }}</span>
                  </div>
                  <span class="estado-badge"
                        [class.pendiente]="p.estado === 'PENDIENTE'"
                        [class.incompleto]="esIncompleto(p)"
                        [class.arrastrada]="p.estado === 'ARRASTRADA'">
                    {{ getEstadoLabel(p) }}
                  </span>
                </div>

                <!-- Detalles del movimiento -->
                <div class="mov-detalle">
                  <div class="mov-detalle-item">
                    <mat-icon>calendar_today</mat-icon>
                    <span>{{ p.fechaMovimiento | date:'dd/MM/yyyy' }}</span>
                  </div>
                  <div class="mov-detalle-item" *ngIf="p.descripcionMovimiento">
                    <mat-icon>description</mat-icon>
                    <span class="mov-desc-text" [title]="p.descripcionMovimiento">{{ p.descripcionMovimiento }}</span>
                  </div>
                  <div class="mov-detalle-item">
                    <mat-icon>attach_money</mat-icon>
                    <span class="mov-monto"
                          [class.debito]="p.tipoMovimiento === 'DEBITO'"
                          [class.credito]="p.tipoMovimiento === 'CREDITO'">
                      {{ p.tipoMovimiento === 'DEBITO' ? '−' : '+' }}
                      {{ p.montoMovimiento | currency:'COP':'symbol':'1.0-2' }}
                    </span>
                  </div>
                </div>

                <!-- Nota de cruce incompleto -->
                <div *ngIf="esIncompleto(p)" class="nota-incompleto">
                  <mat-icon>warning</mat-icon>
                  <span>Cruce incompleto — diferencia: <strong>{{ getDiferenciaIncompleto(p) | currency:'COP':'symbol':'1.0-2' }}</strong></span>
                </div>

                <!-- Nota arrastrada -->
                <div *ngIf="p.estado === 'ARRASTRADA'" class="nota-resuelta arrastrada">
                  <mat-icon>forward</mat-icon>
                  <span>Marcada para el período <strong>{{ p.periodoArrastre }}</strong></span>
                </div>

                <!-- Acciones (solo PENDIENTE o INCOMPLETO) -->
                <div *ngIf="p.estado === 'PENDIENTE' || esIncompleto(p)" class="acciones-row">

                  <!-- Botón Dejar para próximo mes -->
                  <button class="accion-toggle"
                          [class.activo]="mostrandoProximo[p.id]"
                          (click)="toggleProximoMes(p.id)">
                    <mat-icon>{{ mostrandoProximo[p.id] ? 'expand_less' : 'forward' }}</mat-icon>
                    {{ mostrandoProximo[p.id] ? 'Cancelar' : 'Próximo mes' }}
                  </button>

                  <!-- Botón Cruzar -->
                  <button class="accion-toggle cruzar-toggle"
                          [class.activo]="cruzarAbierto === p.id"
                          (click)="toggleCruzar(p.id)">
                    <mat-icon>{{ cruzarAbierto === p.id ? 'expand_less' : 'compare_arrows' }}</mat-icon>
                    {{ cruzarAbierto === p.id ? 'Cancelar' : 'Cruzar' }}
                  </button>

                </div>

                <!-- Formulario próximo mes -->
                <form *ngIf="mostrandoProximo[p.id]"
                      [formGroup]="getArrastrarForm(p.id)"
                      (ngSubmit)="arrastrar(p)"
                      class="proximo-form">
                  <mat-form-field appearance="outline" class="field-periodo">
                    <mat-label>Período destino (YYYY-MM)</mat-label>
                    <input matInput formControlName="periodoDestino" [placeholder]="siguienteMes()">
                    <mat-error>Formato YYYY-MM requerido</mat-error>
                  </mat-form-field>
                  <button mat-flat-button class="arrastrar-btn" type="submit"
                          [disabled]="getArrastrarForm(p.id).invalid || saving[p.id]">
                    <mat-spinner diameter="16" *ngIf="saving[p.id]"></mat-spinner>
                    <span *ngIf="!saving[p.id]">Confirmar</span>
                  </button>
                </form>

                <!-- Panel de cruce inline -->
                <div *ngIf="cruzarAbierto === p.id" class="cruzar-panel">

                  <!-- Opción: Gasto bancario -->
                  <button mat-stroked-button class="gasto-btn"
                          [disabled]="guardando"
                          (click)="cruzarComoGasto(p)">
                    <mat-spinner diameter="14" *ngIf="guardando && guardandoOrigen === p.id && guardandoTipo === 'GASTO_BANCARIO'"></mat-spinner>
                    <mat-icon *ngIf="!(guardando && guardandoOrigen === p.id && guardandoTipo === 'GASTO_BANCARIO')">receipt_long</mat-icon>
                    Marcar como gasto bancario
                  </button>

                  <!-- Lista de otras partidas para cruzar -->
                  <div *ngIf="otrasPartidasPendientes(p.id).length > 0">
                    <p class="cruzar-subtitle">O selecciona movimientos para cruzar con este:</p>
                    <div *ngFor="let otra of otrasPartidasPendientes(p.id)" class="otra-partida"
                         [class.seleccionada]="cruzarSeleccion.has(otra.id)"
                         (click)="toggleCruzarSeleccion(otra.id)">
                      <mat-checkbox [checked]="cruzarSeleccion.has(otra.id)"
                                    (change)="toggleCruzarSeleccion(otra.id)"
                                    (click)="$event.stopPropagation()">
                      </mat-checkbox>
                      <span class="otra-badge" [class.bancario]="otra.tipoOrigen === 'BANCARIO'"
                                               [class.contable]="otra.tipoOrigen === 'CONTABLE'">
                        {{ otra.tipoOrigen === 'BANCARIO' ? 'Bancario' : 'Contable' }}
                      </span>
                      <span class="otra-desc">{{ otra.descripcionMovimiento || ('Partida #' + otra.id) }}</span>
                      <span class="otra-monto" [class.debito]="otra.tipoMovimiento === 'DEBITO'"
                                               [class.credito]="otra.tipoMovimiento === 'CREDITO'">
                        {{ otra.tipoMovimiento === 'DEBITO' ? '−' : '+' }}{{ otra.montoMovimiento | currency:'COP':'symbol':'1.0-0' }}
                      </span>
                    </div>

                    <!-- Resumen de la selección + confirmar -->
                    <div *ngIf="cruzarSeleccion.size > 0" class="cruzar-resumen">
                      <div class="cruzar-resumen-montos">
                        <span>Origen: <strong>{{ montoSignado(p) | currency:'COP':'symbol':'1.0-0' }}</strong></span>
                        <span>Seleccionados: <strong>{{ totalSeleccionados(p.id) | currency:'COP':'symbol':'1.0-0' }}</strong></span>
                        <span class="diferencia" [class.cero]="diferenciaCruce(p) === 0">
                          Diferencia: <strong>{{ diferenciaCruce(p) | currency:'COP':'symbol':'1.0-0' }}</strong>
                        </span>
                      </div>
                      <button mat-flat-button class="confirmar-btn"
                              [disabled]="guardando"
                              (click)="confirmarCruce(p)">
                        <mat-spinner diameter="14" *ngIf="guardando && guardandoOrigen === p.id && guardandoTipo === 'CRUZAR'"></mat-spinner>
                        <mat-icon *ngIf="!(guardando && guardandoOrigen === p.id && guardandoTipo === 'CRUZAR')">done</mat-icon>
                        {{ diferenciaCruce(p) === 0 ? 'Confirmar cruce' : 'Confirmar cruce incompleto' }}
                      </button>
                    </div>
                  </div>

                  <p *ngIf="otrasPartidasPendientes(p.id).length === 0" class="cruzar-empty">
                    No hay otras partidas pendientes para cruzar con este movimiento.
                  </p>
                </div>

              </mat-card-content>
            </mat-card>
          </div>
        </ng-container>

        <div *ngIf="partidasVisibles().length === 0" class="empty-state">
          <mat-icon>check_circle</mat-icon>
          <p>No hay movimientos pendientes para esta conciliación</p>
        </div>

        <!-- ── Partidas de meses anteriores ─────────────────────────────── -->
        <ng-container *ngIf="partidasHistoricas.length > 0">
          <mat-divider class="section-divider"></mat-divider>
          <div class="historicas-header">
            <mat-icon>history</mat-icon>
            <div>
              <h2 class="historicas-title">Movimientos de conciliaciones anteriores</h2>
              <p class="historicas-subtitle">Pendientes de períodos anteriores para la misma cuenta.</p>
            </div>
          </div>
          <div *ngFor="let p of partidasHistoricas" class="partida-card historica">
            <mat-card>
              <mat-card-content>
                <div class="partida-header">
                  <div class="partida-meta">
                    <span class="hist-badge">Conciliación #{{ p.idConciliacion }}</span>
                    <span class="origen-badge" [class.bancario]="p.tipoOrigen === 'BANCARIO'"
                                                [class.contable]="p.tipoOrigen === 'CONTABLE'">
                      <mat-icon>{{ p.tipoOrigen === 'BANCARIO' ? 'account_balance' : 'book' }}</mat-icon>
                      {{ p.tipoOrigen === 'BANCARIO' ? 'Bancario' : 'Contable' }}
                    </span>
                  </div>
                  <span class="estado-badge pendiente">Pendiente</span>
                </div>
                <div class="mov-detalle">
                  <div class="mov-detalle-item">
                    <mat-icon>calendar_today</mat-icon>
                    <span>{{ p.fechaMovimiento | date:'dd/MM/yyyy' }}</span>
                  </div>
                  <div class="mov-detalle-item" *ngIf="p.descripcionMovimiento">
                    <mat-icon>description</mat-icon>
                    <span class="mov-desc-text">{{ p.descripcionMovimiento }}</span>
                  </div>
                  <div class="mov-detalle-item">
                    <mat-icon>attach_money</mat-icon>
                    <span class="mov-monto"
                          [class.debito]="p.tipoMovimiento === 'DEBITO'"
                          [class.credito]="p.tipoMovimiento === 'CREDITO'">
                      {{ p.tipoMovimiento === 'DEBITO' ? '−' : '+' }}
                      {{ p.montoMovimiento | currency:'COP':'symbol':'1.0-2' }}
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
    .page-container { padding: 32px; max-width: 960px; }
    .page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 24px; }
    .page-title { font-size: 24px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 13px; color: #6b7a8d; margin: 0; }
    .back-btn { color: #6b7a8d; }
    .loading-container { display: flex; justify-content: center; padding: 40px; }

    .summary-row { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
    .count-badge { padding: 5px 14px; border-radius: 20px; font-size: 13px; font-weight: 500; }
    .count-badge.pending    { background: #fef3c7; color: #92400e; }
    .count-badge.incomplete, .estado-badge.incompleto { background: #fee2e2; color: #991b1b; }
    .count-badge.dragged, .estado-badge.arrastrada { background: #e0e7ff; color: #3730a3; }
    .count-badge.historical, .hist-badge { background: #f3f4f6; color: #374151; }

    .gastos-card { margin-bottom: 20px; border-left: 4px solid #f59e0b !important; border-radius: 10px !important; box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important; }
    .gastos-row { display: flex; align-items: center; gap: 16px; }
    .gastos-icon { font-size: 32px; width: 32px; height: 32px; color: #f59e0b; }
    .gastos-label { margin: 0 0 4px; font-size: 13px; color: #6b7a8d; }
    .gastos-monto { margin: 0; font-size: 22px; font-weight: 700; color: #92400e; }

    .partida-card { margin-bottom: 14px; }
    .partida-card mat-card { border-radius: 10px !important; box-shadow: 0 2px 6px rgba(0,0,0,0.06) !important; }
    .partida-card.incompleto mat-card { border-left: 4px solid #ef4444 !important; }
    .partida-card.resuelta mat-card { opacity: 0.65; }
    .partida-card.historica mat-card { border: 1px dashed #d1d5db !important; background: #fafafa; }

    .partida-header { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
    .partida-meta { display: flex; align-items: center; gap: 10px; flex: 1; flex-wrap: wrap; }

    .origen-badge { display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 6px; font-size: 13px; font-weight: 500; }
    .origen-badge mat-icon { font-size: 15px; width: 15px; height: 15px; }
    .origen-badge.bancario, .otra-badge.bancario { background: #dbeafe; color: #1e40af; }
    .origen-badge.contable, .otra-badge.contable { background: #ede9fe; color: #5b21b6; }
    .partida-id { font-size: 12px; color: #9ca3af; }
    .hist-badge { padding: 3px 10px; border-radius: 6px; font-size: 11px; font-weight: 500; }

    .estado-badge { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; white-space: nowrap; margin-left: auto; }
    .estado-badge.pendiente  { background: #fef3c7; color: #b45309; }

    .mov-detalle {
      display: flex; gap: 20px; flex-wrap: wrap; align-items: center;
      background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 8px;
      padding: 10px 14px; margin-bottom: 10px;
    }
    .mov-detalle-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; }
    .mov-detalle-item mat-icon { font-size: 15px; width: 15px; height: 15px; color: #9ca3af; }
    .mov-desc-text { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .mov-monto { font-weight: 600; }
    .mov-monto.debito,  .otra-monto.debito  { color: #dc2626; }
    .mov-monto.credito, .otra-monto.credito { color: #16a34a; }

    .nota-incompleto {
      display: flex; align-items: center; gap: 8px;
      background: #fef2f2; border: 1px solid #fecaca; border-radius: 8px;
      padding: 8px 14px; font-size: 13px; color: #991b1b; margin-bottom: 10px;
    }
    .nota-incompleto mat-icon { font-size: 16px; width: 16px; height: 16px; color: #ef4444; flex-shrink: 0; }
    .nota-resuelta { display: flex; align-items: center; gap: 8px; border-radius: 8px; padding: 8px 14px; font-size: 13px; margin-bottom: 8px; }
    .nota-resuelta mat-icon { font-size: 18px; width: 18px; height: 18px; flex-shrink: 0; }
    .nota-resuelta.arrastrada { background: #ede9fe; border: 1px solid #c4b5fd; color: #5b21b6; }

    .acciones-row { display: flex; gap: 10px; margin-top: 6px; flex-wrap: wrap; }
    .accion-toggle {
      display: inline-flex; align-items: center; gap: 6px;
      border: 1px solid #d1d5db; border-radius: 8px;
      background: #fff; color: #6b7a8d; padding: 6px 14px;
      font-size: 13px; cursor: pointer; transition: all 0.15s;
    }
    .accion-toggle mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .accion-toggle:hover { border-color: #5b21b6; color: #5b21b6; background: #f5f3ff; }
    .accion-toggle.activo { border-color: #5b21b6; color: #5b21b6; background: #ede9fe; }
    .accion-toggle.cruzar-toggle:hover { border-color: #1d4ed8; color: #1d4ed8; background: #eff6ff; }
    .accion-toggle.cruzar-toggle.activo { border-color: #1d4ed8; color: #1d4ed8; background: #dbeafe; }

    .proximo-form { display: flex; align-items: center; gap: 12px; margin-top: 12px; flex-wrap: wrap; }
    .field-periodo { width: 220px; }
    .arrastrar-btn { background: #5b21b6 !important; color: #fff !important; border-radius: 8px !important; height: 42px; }

    /* Panel de cruce inline */
    .cruzar-panel {
      margin-top: 12px;
      background: #f0f7ff;
      border: 1px solid #bfdbfe;
      border-radius: 10px;
      padding: 16px;
    }
    .gasto-btn {
      border-color: #f59e0b !important; color: #92400e !important;
      background: #fffbeb !important; border-radius: 8px !important;
      font-weight: 500 !important; margin-bottom: 16px;
      display: flex; align-items: center; gap: 6px;
    }
    .gasto-btn mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .cruzar-subtitle { font-size: 13px; color: #374151; margin: 0 0 10px; font-weight: 500; }
    .cruzar-empty { font-size: 13px; color: #6b7a8d; margin: 8px 0 0; }

    .otra-partida {
      display: flex; align-items: center; gap: 10px;
      background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
      padding: 8px 12px; margin-bottom: 6px; cursor: pointer; transition: all 0.12s;
    }
    .otra-partida:hover { border-color: #93c5fd; background: #f0f7ff; }
    .otra-partida.seleccionada { border-color: #2563eb; background: #eff6ff; }
    .otra-badge { padding: 2px 8px; border-radius: 5px; font-size: 11px; font-weight: 600; flex-shrink: 0; }
    .otra-desc { flex: 1; font-size: 13px; color: #374151; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .otra-monto { font-size: 13px; font-weight: 600; flex-shrink: 0; }

    .cruzar-resumen {
      margin-top: 12px; padding: 12px; background: #fff;
      border: 1px solid #bfdbfe; border-radius: 8px;
      display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
    }
    .cruzar-resumen-montos { display: flex; gap: 20px; flex-wrap: wrap; font-size: 13px; color: #374151; }
    .diferencia { color: #dc2626; }
    .diferencia.cero { color: #16a34a; }
    .confirmar-btn { background: #1d4ed8 !important; color: #fff !important; border-radius: 8px !important; display: flex; align-items: center; gap: 6px; }
    .confirmar-btn mat-icon { font-size: 18px; width: 18px; height: 18px; }

.empty-state { text-align: center; padding: 60px; color: #9ca3af; }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; display: block; margin: 0 auto 12px; color: #22c55e; }
    .section-divider { margin: 32px 0 24px; }
    .historicas-header { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 20px; }
    .historicas-header mat-icon { color: #6b7a8d; margin-top: 4px; font-size: 22px; }
    .historicas-title { font-size: 17px; font-weight: 600; color: #374151; margin: 0 0 4px; }
    .historicas-subtitle { font-size: 13px; color: #6b7a8d; margin: 0; }
  `]
})
export class PartidasComponent implements OnInit {
  idConciliacion!: number;
  periodoConciliacion: string | null = null;
  partidas: any[] = [];
  partidasHistoricas: any[] = [];
  loading = true;
  guardando = false;
  guardandoOrigen: number | null = null;
  guardandoTipo: string | null = null;
  totalGastosBancarios = 0;

  saving: Record<number, boolean> = {};
  mostrandoProximo: Record<number, boolean> = {};
  cruzarAbierto: number | null = null;
  cruzarSeleccion = new Set<number>();
private arrastrarForms: Record<number, FormGroup> = {};

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
    private fb: FormBuilder,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.idConciliacion = +this.route.snapshot.paramMap.get('id')!;
    // El período debe estar disponible ANTES de cargar() -- ese método arma los
    // formularios de "próximo mes" usando siguienteMes(), que depende de él.
    this.api.obtenerConciliacion(this.idConciliacion).subscribe({
      next: res => {
        this.periodoConciliacion = res.data.periodo;
        this.cargar();
      },
      error: () => this.cargar()  // periodoConciliacion queda null; siguienteMes() cae al fallback de hoy
    });
  }

  cargar(): void {
    this.loading = true;
    this.api.listarPartidas(this.idConciliacion).subscribe({
      next: res => {
        this.partidas = res.data;
        this.partidas.forEach(p => {
          if (p.estado === 'PENDIENTE' || this.esIncompleto(p)) {
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
    this.actualizarGastos();
  }

  actualizarGastos(): void {
    this.api.listarGastosAgrupadosConciliacion(this.idConciliacion).subscribe({
      next: res => {
        this.totalGastosBancarios = (res.data as any[]).reduce((sum: number, g: any) => sum + (g.total ?? 0), 0);
      }
    });
  }

  // ── Filtros ───────────────────────────────────────────────────────────────

  esIncompleto(p: any): boolean {
    return p.estado === 'CRUZADA' && p.justificacion?.startsWith('INCOMPLETO|');
  }

  partidasVisibles(): any[] {
    return this.partidas.filter(p =>
      p.estado === 'PENDIENTE' || p.estado === 'ARRASTRADA' || this.esIncompleto(p)
    );
  }

  otrasPartidasPendientes(idOrigen: number): any[] {
    return this.partidas.filter(p => p.id !== idOrigen && (p.estado === 'PENDIENTE' || this.esIncompleto(p)));
  }

  // ── Toggle paneles ────────────────────────────────────────────────────────

  toggleProximoMes(id: number): void {
    this.mostrandoProximo[id] = !this.mostrandoProximo[id];
    if (this.mostrandoProximo[id]) this.cruzarAbierto = null;
  }

  toggleCruzar(id: number): void {
    if (this.cruzarAbierto === id) {
      this.cruzarAbierto = null;
      this.cruzarSeleccion.clear();
    } else {
      this.cruzarAbierto = id;
      this.cruzarSeleccion.clear();
      this.mostrandoProximo[id] = false;
    }
  }

  toggleCruzarSeleccion(id: number): void {
    if (this.cruzarSeleccion.has(id)) {
      this.cruzarSeleccion.delete(id);
    } else {
      this.cruzarSeleccion.add(id);
    }
  }

  // ── Montos y diferencias ─────────────────────────────────────────────────

  montoSignado(p: any): number {
    const m = p.montoMovimiento ?? 0;
    return p.tipoMovimiento === 'DEBITO' ? -m : m;
  }

  totalSeleccionados(idOrigen: number): number {
    return this.partidas
      .filter(p => this.cruzarSeleccion.has(p.id))
      .reduce((s, p) => s + Math.abs(p.montoMovimiento ?? 0), 0);
  }

  diferenciaCruce(origen: any): number {
    return Math.abs(origen.montoMovimiento ?? 0) - this.totalSeleccionados(origen.id);
  }

  // ── Acciones ──────────────────────────────────────────────────────────────

  cruzarComoGasto(p: any): void {
    if (this.guardando) return;
    this.guardando = true;
    this.guardandoOrigen = p.id;
    this.guardandoTipo = 'GASTO_BANCARIO';
    this.api.cruzarPartidas(this.idConciliacion, p.id, [], 'GASTO_BANCARIO').subscribe({
      next: res => {
        res.data.forEach((u: any) => this.actualizarLocal(u));
        this.cruzarAbierto = null;
        this.cruzarSeleccion.clear();
        this.guardando = false;
        this.guardandoOrigen = null;
        this.guardandoTipo = null;
        this.actualizarGastos();
        this.snackBar.open('Marcado como gasto bancario y configuración guardada', 'Cerrar',
          { duration: 3500, panelClass: 'snack-success' });
      },
      error: err => {
        this.guardando = false;
        this.guardandoOrigen = null;
        this.guardandoTipo = null;
        this.snackBar.open(err.error?.message ?? 'Error al marcar gasto bancario', 'Cerrar', { duration: 4000 });
      }
    });
  }

  confirmarCruce(origen: any): void {
    if (this.guardando || this.cruzarSeleccion.size === 0) return;
    this.guardando = true;
    this.guardandoOrigen = origen.id;
    this.guardandoTipo = 'CRUZAR';
    const idsDestino = Array.from(this.cruzarSeleccion);
    this.api.cruzarPartidas(this.idConciliacion, origen.id, idsDestino, 'CRUZAR').subscribe({
      next: res => {
        res.data.forEach((u: any) => this.actualizarLocal(u));
        this.cruzarAbierto = null;
        this.cruzarSeleccion.clear();
        this.guardando = false;
        this.guardandoOrigen = null;
        this.guardandoTipo = null;
        const esCompleto = this.diferenciaCruce(origen) === 0;
        const msg = esCompleto ? 'Partidas cruzadas correctamente' : 'Cruce incompleto guardado — la diferencia queda pendiente';
        this.snackBar.open(msg, 'Cerrar', { duration: 3500, panelClass: 'snack-success' });
      },
      error: err => {
        this.guardando = false;
        this.guardandoOrigen = null;
        this.guardandoTipo = null;
        this.snackBar.open(err.error?.message ?? 'Error al cruzar partidas', 'Cerrar', { duration: 4000 });
      }
    });
  }

  private actualizarLocal(updated: any): void {
    const p = this.partidas.find(x => x.id === updated.id);
    if (p) {
      p.estado = updated.estado;
      p.justificacion = updated.justificacion;
      p.periodoArrastre = updated.periodoArrastre;
    } else {
      // Partida nueva que no existía localmente -- p.ej. el "resto" de un cruce
      // incompleto, que el backend crea y devuelve en la misma respuesta.
      this.partidas.push(updated);
    }
  }

  // ── Próximo mes ──────────────────────────────────────────────────────────

  getArrastrarForm(id: number): FormGroup {
    if (!this.arrastrarForms[id]) {
      this.arrastrarForms[id] = this.fb.group({
        periodoDestino: [this.siguienteMes(), [
          Validators.required,
          Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/)
        ]]
      });
    }
    return this.arrastrarForms[id];
  }

  arrastrar(partida: any): void {
    const form = this.arrastrarForms[partida.id];
    if (form.invalid) return;
    this.saving[partida.id] = true;
    const periodoDestino = form.value.periodoDestino;
    this.api.arrastrarPartida(this.idConciliacion, partida.id, periodoDestino).subscribe({
      next: () => {
        partida.estado = 'ARRASTRADA';
        partida.periodoArrastre = periodoDestino;
        this.mostrandoProximo[partida.id] = false;
        this.saving[partida.id] = false;
        this.snackBar.open(`Marcado para el período ${periodoDestino}`, 'Cerrar',
          { duration: 3000, panelClass: 'snack-success' });
      },
      error: err => {
        this.saving[partida.id] = false;
        this.snackBar.open(err.error?.message ?? 'Error al arrastrar partida', 'Cerrar', { duration: 3000 });
      }
    });
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Mes siguiente al PERÍODO DE LA CONCILIACIÓN, no a la fecha real de hoy -- si el
   * usuario está conciliando julio en agosto (o en cualquier otro momento posterior),
   * "próximo mes" debe proponer agosto (el mes después de julio), no el mes después de
   * la fecha en que efectivamente hace clic.
   */
  siguienteMes(): string {
    if (this.periodoConciliacion && /^\d{4}-\d{2}$/.test(this.periodoConciliacion)) {
      const [anioStr, mesStr] = this.periodoConciliacion.split('-');
      let anio = parseInt(anioStr, 10);
      let mes = parseInt(mesStr, 10) + 1;
      if (mes > 12) { mes = 1; anio += 1; }
      return `${anio}-${String(mes).padStart(2, '0')}`;
    }
    // Fallback si por algún motivo no se pudo cargar el período de la conciliación.
    const d = new Date();
    d.setMonth(d.getMonth() + 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  }

  getEstadoLabel(p: any): string {
    if (this.esIncompleto(p)) return 'Cruce incompleto';
    return ({ PENDIENTE: 'Pendiente', ARRASTRADA: 'Próximo mes' } as any)[p.estado] ?? p.estado;
  }

  getDiferenciaIncompleto(p: any): number {
    if (!p.justificacion) return 0;
    const match = p.justificacion.match(/diferencia:([+-]?[\d.]+)/);
    return match ? parseFloat(match[1]) : 0;
  }

  getPendientes(): number  { return this.partidas.filter(p => p.estado === 'PENDIENTE').length; }
  getIncompletos(): number { return this.partidas.filter(p => this.esIncompleto(p)).length; }
  getArrastradas(): number { return this.partidas.filter(p => p.estado === 'ARRASTRADA').length; }
}
