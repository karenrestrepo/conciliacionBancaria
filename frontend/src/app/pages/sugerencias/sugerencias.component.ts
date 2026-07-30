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
import { MatCheckboxModule } from '@angular/material/checkbox';
import { SelectionModel } from '@angular/cdk/collections';
import { CurrencyPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Conciliacion, Sugerencia, MovimientoAgrupado, GrupoGastoBancario } from '../../core/models';

@Component({
  selector: 'app-sugerencias',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatCardModule, MatTableModule,
    MatIconModule, MatButtonModule, MatChipsModule,
    MatProgressSpinnerModule, MatTooltipModule, MatDividerModule,
    MatSnackBarModule, MatCheckboxModule, CurrencyPipe
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
            <mat-icon>pending_actions</mat-icon>
            Ver Pendientes
          </button>
          <button mat-stroked-button class="gastos-btn"
                  *ngIf="conciliacion"
                  (click)="toggleGastos()">
            <mat-icon>account_balance</mat-icon>
            Gastos bancarios
            <span class="gastos-badge" *ngIf="totalGastos > 0">{{ gruposGastos.length }}</span>
          </button>
          <!-- Reprocesar motor sin re-subir archivos -->
          <button mat-stroked-button class="reprocesar-btn"
                  *ngIf="conciliacion?.estado === 'EN_REVISION' && canReview()"
                  (click)="reprocesar()" [disabled]="reprocesando">
            <mat-icon>refresh</mat-icon>
            {{ reprocesando ? 'Procesando...' : 'Reprocesar' }}
          </button>
          <!-- Subir nuevo auxiliar en EN_REVISION -->
          <label *ngIf="conciliacion?.estado === 'EN_REVISION' && canReview()"
                 class="upload-aux-btn" [class.uploading]="subiendoAuxiliar">
            <mat-icon>upload_file</mat-icon>
            {{ subiendoAuxiliar ? 'Procesando...' : 'Subir auxiliar' }}
            <input type="file" accept=".csv,.xls,.xlsx" (change)="onAuxiliarChange($event)" hidden>
          </label>
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

        <!-- Barra de acciones masivas -->
        <div class="bulk-toolbar" *ngIf="selection.hasValue() && canReview()">
          <span class="bulk-count">{{ selection.selected.length }} seleccionada(s)</span>
          <button mat-flat-button class="bulk-accept-btn" (click)="aceptarSeleccionadas()" [disabled]="aceptandoLote">
            <mat-spinner diameter="16" *ngIf="aceptandoLote"></mat-spinner>
            <mat-icon *ngIf="!aceptandoLote">done_all</mat-icon>
            {{ aceptandoLote ? 'Aprobando...' : 'Aprobar seleccionadas' }}
          </button>
          <button mat-stroked-button (click)="selection.clear()" class="bulk-clear-btn">
            Limpiar selección
          </button>
        </div>

        <!-- Cruces manuales completados (plegable) -->
        <div *ngIf="partidasCruzadas.length > 0" class="cruces-toggle-wrap">
          <button class="cruces-toggle-btn" (click)="mostrarCruces = !mostrarCruces">
            <div class="cruces-toggle-left">
              <mat-icon class="cruces-toggle-icon">compare_arrows</mat-icon>
              <span class="cruces-toggle-label">Cruces manuales</span>
              <span class="cruces-toggle-count">{{ partidasCruzadas.length }}</span>
            </div>
            <mat-icon class="cruces-toggle-arrow">{{ mostrarCruces ? 'expand_less' : 'expand_more' }}</mat-icon>
          </button>

          <section *ngIf="mostrarCruces" class="cruces-section">
            <mat-card *ngFor="let p of partidasCruzadas" class="cruce-card">
              <mat-card-content>
                <div class="cruce-row">
                  <span class="origen-badge" [class.bancario]="p.tipoOrigen === 'BANCARIO'"
                                              [class.contable]="p.tipoOrigen === 'CONTABLE'">
                    <mat-icon>{{ p.tipoOrigen === 'BANCARIO' ? 'account_balance' : 'book' }}</mat-icon>
                    {{ p.tipoOrigen === 'BANCARIO' ? 'Bancario' : 'Contable' }}
                  </span>
                  <span class="cruce-fecha">{{ p.fechaMovimiento | date:'dd/MM/yyyy' }}</span>
                  <span class="cruce-desc" [title]="p.descripcionMovimiento">{{ p.descripcionMovimiento }}</span>
                  <span class="cruce-monto"
                        [class.debito]="p.tipoMovimiento === 'DEBITO'"
                        [class.credito]="p.tipoMovimiento === 'CREDITO'">
                    {{ p.tipoMovimiento === 'DEBITO' ? '−' : '+' }}{{ p.montoMovimiento | currency:'COP':'symbol':'1.0-0' }}
                  </span>
                  <span class="cruce-estado">Cruzada</span>
                </div>
              </mat-card-content>
            </mat-card>
          </section>
        </div>

        <!-- Panel gastos bancarios agrupados -->
        <section *ngIf="mostrarGastos" class="gastos-section">
          <div class="gastos-header">
            <div class="gastos-title-row">
              <mat-icon class="gastos-title-icon">account_balance</mat-icon>
              <h2 class="gastos-title">Gastos bancarios agrupados</h2>
              <span class="gastos-hint">Movimientos del extracto agrupados antes de la conciliación</span>
            </div>
            <button mat-icon-button (click)="mostrarGastos = false" class="close-gastos">
              <mat-icon>close</mat-icon>
            </button>
          </div>

          <div *ngIf="cargandoGastos" class="loading-center">
            <mat-spinner diameter="28"></mat-spinner>
          </div>

          <div *ngIf="!cargandoGastos && gruposGastos.length === 0" class="gastos-empty">
            <mat-icon>info_outline</mat-icon>
            <span>No hay gastos bancarios agrupados en esta conciliación.
              Configura las descripciones en la cuenta correspondiente antes de subir el extracto.</span>
          </div>

          <ng-container *ngIf="!cargandoGastos && gruposGastos.length > 0">
            <mat-card *ngFor="let grupo of gruposGastos" class="grupo-card">
              <div class="grupo-header" (click)="toggleGrupo(grupo.descripcion)">
                <div class="grupo-left">
                  <mat-icon class="grupo-icon">label_outline</mat-icon>
                  <span class="grupo-desc">{{ grupo.descripcion }}</span>
                </div>
                <div class="grupo-right">
                  <span class="grupo-count">{{ grupo.count }} registro{{ grupo.count !== 1 ? 's' : '' }}</span>
                  <span class="grupo-total" [ngClass]="grupo.tipo === 'DEBITO' ? 'monto-debito' : 'monto-credito'">
                    {{ grupo.tipo === 'DEBITO' ? '−' : '+' }}
                    {{ grupo.total | currency:'COP':'symbol':'1.0-2' }}
                  </span>
                  <mat-icon class="expand-icon">{{ isGrupoExpanded(grupo.descripcion) ? 'expand_less' : 'expand_more' }}</mat-icon>
                </div>
              </div>
              <div *ngIf="isGrupoExpanded(grupo.descripcion)" class="grupo-detalle">
                <mat-divider></mat-divider>
                <table class="detalle-table">
                  <thead>
                    <tr>
                      <th>Fecha</th>
                      <th>Descripción en extracto</th>
                      <th class="th-monto">Monto</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr *ngFor="let mov of grupo.movimientos">
                      <td class="td-fecha">{{ mov.fecha | date:'dd/MM/yyyy' }}</td>
                      <td class="td-desc">{{ mov.descripcion }}</td>
                      <td class="td-monto" [ngClass]="mov.tipo === 'DEBITO' ? 'monto-debito' : 'monto-credito'">
                        {{ mov.tipo === 'DEBITO' ? '−' : '+' }}
                        {{ mov.monto | currency:'COP':'symbol':'1.0-2' }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </mat-card>
            <div class="gastos-total-row">
              <span class="gastos-total-label">Total general gastos bancarios</span>
              <span class="gastos-total-valor monto-debito">
                − {{ totalGastos | currency:'COP':'symbol':'1.0-2' }}
              </span>
            </div>
          </ng-container>
        </section>

        <!-- Tabla -->
        <mat-card class="table-card">
          <div *ngIf="sugerencias.length === 0" class="empty-state">
            <mat-icon>psychology</mat-icon>
            <p>No hay sugerencias para esta conciliación</p>
            <p class="empty-hint">Asegúrese de haber cargado el extracto bancario y el libro auxiliar</p>
          </div>

          <table mat-table [dataSource]="sugerencias"
                 *ngIf="sugerencias.length > 0" class="data-table">

            <!-- Checkbox de selección -->
            <ng-container matColumnDef="select">
              <th mat-header-cell *matHeaderCellDef>
                <mat-checkbox
                  *ngIf="canReview()"
                  [checked]="isAllPendientesSelected()"
                  [indeterminate]="selection.hasValue() && !isAllPendientesSelected()"
                  (change)="toggleAllPendientes($event.checked)"
                  matTooltip="Seleccionar todas las pendientes">
                </mat-checkbox>
              </th>
              <td mat-cell *matCellDef="let row">
                <mat-checkbox
                  *ngIf="row.estado === 'PENDIENTE_REVISION' && canReview()"
                  [checked]="selection.isSelected(row)"
                  (change)="selection.toggle(row)">
                </mat-checkbox>
              </td>
            </ng-container>

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
              <th mat-header-cell *matHeaderCellDef>Movimiento Bancario (Extracto)</th>
              <td mat-cell *matCellDef="let row">
                <div class="mov-info">
                  <span class="mov-fecha">{{ row.fechaBancario | date:'dd/MM/yyyy' }}</span>
                  <span class="mov-desc" [title]="row.descripcionBancario">{{ row.descripcionBancario }}</span>
                  <span class="mov-monto" [ngClass]="getTipoClass(row.tipoBancario)">
                    {{ row.tipoBancario === 'DEBITO' ? '−' : '+' }}
                    {{ row.montoBancario | currency:'COP':'symbol':'1.0-2' }}
                  </span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="movContable">
              <th mat-header-cell *matHeaderCellDef>Movimiento Contable (Auxiliar)</th>
              <td mat-cell *matCellDef="let row">
                <div class="mov-info">
                  <span class="mov-fecha">{{ row.fechaContable | date:'dd/MM/yyyy' }}</span>
                  <span class="mov-desc" [title]="row.descripcionContable">{{ row.descripcionContable }}</span>
                  <span class="mov-monto" [ngClass]="getTipoClass(row.tipoContable)">
                    {{ row.tipoContable === 'DEBITO' ? '−' : '+' }}
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

    .header-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }

    .back-btn { color: #6b7a8d; border-color: #d1d5db !important; border-radius: 8px !important; }

    .revision-btn {
      background: #1a2332 !important; color: #fff !important;
      border-radius: 8px !important; height: 42px; gap: 6px;
    }
    .partidas-btn {
      border-color: #1a2332 !important; color: #1a2332 !important;
      border-radius: 8px !important; height: 42px; gap: 6px;
    }
    .reprocesar-btn {
      border-color: #3d7ebf !important; color: #3d7ebf !important;
      border-radius: 8px !important; height: 42px; gap: 6px;
    }
    .cerrar-btn {
      background: #c0392b !important; color: #fff !important;
      border-radius: 8px !important; height: 42px; gap: 6px;
    }

    .upload-aux-btn {
      display: inline-flex; align-items: center; gap: 6px;
      border: 1px solid #3d7ebf; color: #3d7ebf;
      border-radius: 8px; height: 42px; padding: 0 16px;
      font-size: 14px; font-weight: 500; cursor: pointer;
      transition: background 0.2s;
    }
    .upload-aux-btn:hover { background: #eff6ff; }
    .upload-aux-btn.uploading { opacity: 0.7; cursor: default; }

    .loading-container { display: flex; justify-content: center; padding: 60px; }

    .summary-grid {
      display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px;
    }

    .summary-card {
      display: flex !important; flex-direction: row !important; align-items: center;
      gap: 14px; padding: 16px 20px !important;
      border-radius: 10px !important; box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
    }

    .summary-icon {
      width: 40px; height: 40px; border-radius: 8px;
      display: flex; align-items: center; justify-content: center; flex-shrink: 0;
    }
    .summary-icon mat-icon { color: #fff; font-size: 20px; }
    .blue { background: #3d7ebf; } .amber { background: #f59e0b; }
    .green { background: #22c55e; } .red { background: #e53935; }

    .summary-value { display: block; font-size: 24px; font-weight: 700; color: #1a2332; }
    .summary-label { display: block; font-size: 12px; color: #6b7a8d; }

    .bulk-toolbar {
      display: flex; align-items: center; gap: 12px;
      background: #eff6ff; border: 1px solid #bfdbfe;
      border-radius: 10px; padding: 12px 20px; margin-bottom: 16px;
    }
    .bulk-count { font-size: 14px; font-weight: 600; color: #1e40af; flex: 1; }
    .bulk-accept-btn {
      background: #16a34a !important; color: #fff !important;
      border-radius: 8px !important; gap: 6px; height: 38px;
    }
    .bulk-clear-btn { border-radius: 8px !important; }

    .table-card {
      border-radius: 10px !important; box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      overflow: hidden;
    }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 60px; color: #6b7a8d;
    }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: #b0bec5; margin-bottom: 12px; }
    .empty-hint { font-size: 13px; color: #9ca3af; margin: 4px 0 0; }

    .data-table { width: 100%; }
    .mat-mdc-header-row { background: #f8fafc; }
    .mat-mdc-header-cell {
      font-size: 12px !important; font-weight: 600 !important;
      color: #6b7a8d !important; text-transform: uppercase; letter-spacing: 0.5px;
    }
    .mat-mdc-cell { padding: 12px 16px !important; vertical-align: middle; }
    .data-row:hover { background: #f8fafc; }
    .row-aceptada { background: #f0fdf4; }
    .row-rechazada { background: #fef2f2; }

    .confianza-wrap { display: flex; align-items: center; gap: 8px; min-width: 100px; }
    .confianza-bar { flex: 1; height: 6px; background: #e5e7eb; border-radius: 3px; overflow: hidden; }
    .confianza-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }
    .conf-high { background: #22c55e; } .conf-mid { background: #f59e0b; } .conf-low { background: #e53935; }
    .confianza-pct { font-size: 12px; font-weight: 600; color: #1a2332; white-space: nowrap; }

    .criterio-badge { padding: 3px 10px; border-radius: 20px; font-size: 11px; font-weight: 500; white-space: nowrap; }
    .crit-exacto { background: #dbeafe; color: #1e40af; }
    .crit-fecha { background: #e0e7ff; color: #3730a3; }
    .crit-aprox { background: #fef3c7; color: #92400e; }

    .mov-info { display: flex; flex-direction: column; gap: 2px; }
    .mov-fecha { font-size: 11px; color: #9ca3af; }
    .mov-desc { font-size: 13px; color: #1a2332; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .mov-monto { font-size: 13px; font-weight: 600; }
    .tipo-debito { color: #e53935; } .tipo-credito { color: #22c55e; }

    .estado-chip { padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 500; }
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

    /* Cruces manuales */
    .cruces-toggle-wrap { margin-bottom: 16px; }
    .cruces-toggle-btn {
      display: flex; align-items: center; justify-content: space-between;
      width: 100%; background: #eff6ff; border: 1.5px solid #bfdbfe;
      border-radius: 10px; padding: 12px 16px;
      cursor: pointer; font-size: 14px; color: #1e40af;
      transition: all 0.15s;
    }
    .cruces-toggle-btn:hover { background: #dbeafe; border-color: #93c5fd; }
    .cruces-toggle-left { display: flex; align-items: center; gap: 10px; }
    .cruces-toggle-icon { font-size: 20px; width: 20px; height: 20px; color: #1d4ed8; }
    .cruces-toggle-label { font-weight: 600; }
    .cruces-toggle-count {
      background: #1d4ed8; color: #fff;
      padding: 1px 8px; border-radius: 12px; font-size: 12px; font-weight: 600;
    }
    .cruces-toggle-arrow { color: #1d4ed8; }
    .cruces-section {
      margin-top: 8px; border: 1.5px solid #bfdbfe;
      border-radius: 10px; padding: 16px 20px; background: #f0f7ff;
    }
    .cruce-card { margin-bottom: 8px !important; border-radius: 8px !important; box-shadow: 0 1px 4px rgba(0,0,0,0.05) !important; }
    .cruce-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .cruce-fecha { font-size: 12px; color: #6b7a8d; flex-shrink: 0; }
    .cruce-desc { flex: 1; font-size: 13px; color: #374151; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cruce-monto { font-size: 13px; font-weight: 600; flex-shrink: 0; }
    .cruce-monto.debito  { color: #dc2626; }
    .cruce-monto.credito { color: #16a34a; }
    .cruce-estado { background: #dbeafe; color: #1e40af; padding: 2px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; flex-shrink: 0; }
    .origen-badge { display: flex; align-items: center; gap: 5px; padding: 3px 10px; border-radius: 6px; font-size: 12px; font-weight: 500; flex-shrink: 0; }
    .origen-badge mat-icon { font-size: 13px; width: 13px; height: 13px; }
    .origen-badge.bancario { background: #dbeafe; color: #1e40af; }
    .origen-badge.contable { background: #ede9fe; color: #5b21b6; }

    .gastos-btn {
      border-color: #7c3aed !important; color: #7c3aed !important;
      border-radius: 8px !important; height: 42px; gap: 6px; position: relative;
    }
    .gastos-badge {
      background: #7c3aed; color: #fff;
      border-radius: 10px; font-size: 11px; font-weight: 700;
      padding: 1px 6px; margin-left: 4px;
    }

    .gastos-section {
      margin-top: 28px;
      border: 1.5px solid #ede9fe;
      border-radius: 12px;
      padding: 20px 24px;
      background: #faf5ff;
    }

    .gastos-header {
      display: flex; justify-content: space-between; align-items: flex-start;
      margin-bottom: 20px;
    }
    .gastos-title-row {
      display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
    }
    .gastos-title-icon { color: #7c3aed; }
    .gastos-title { font-size: 17px; font-weight: 600; color: #1a2332; margin: 0; }
    .gastos-hint { font-size: 12px; color: #7c3aed; background: #ede9fe; padding: 2px 10px; border-radius: 20px; }
    .close-gastos { color: #6b7a8d !important; }

    .loading-center { display: flex; justify-content: center; padding: 28px; }

    .gastos-empty {
      display: flex; align-items: flex-start; gap: 10px;
      background: #f3f4f6; border-radius: 8px; padding: 16px;
      font-size: 13px; color: #6b7a8d;
    }
    .gastos-empty mat-icon { color: #9ca3af; flex-shrink: 0; }

    .grupo-card {
      border-radius: 10px !important;
      box-shadow: 0 1px 4px rgba(0,0,0,0.06) !important;
      margin-bottom: 10px !important;
      overflow: hidden;
    }

    .grupo-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 14px 18px; cursor: pointer; user-select: none;
      transition: background 0.15s;
    }
    .grupo-header:hover { background: #f5f3ff; }

    .grupo-left { display: flex; align-items: center; gap: 10px; }
    .grupo-icon { font-size: 18px; width: 18px; height: 18px; color: #7c3aed; }
    .grupo-desc { font-size: 14px; font-weight: 600; color: #1a2332; font-family: monospace; }

    .grupo-right { display: flex; align-items: center; gap: 16px; }
    .grupo-count { font-size: 13px; color: #6b7a8d; }
    .grupo-total { font-size: 15px; font-weight: 700; }
    .monto-debito  { color: #dc2626; }
    .monto-credito { color: #16a34a; }
    .expand-icon { color: #7c3aed; transition: transform 0.2s; }

    .grupo-detalle { padding: 0 18px 14px; }

    .detalle-table {
      width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 13px;
    }
    .detalle-table th {
      text-align: left; font-size: 11px; font-weight: 600; color: #6b7a8d;
      text-transform: uppercase; letter-spacing: 0.4px;
      padding: 6px 12px; border-bottom: 1px solid #e5e7eb;
    }
    .detalle-table tr:hover td { background: #faf5ff; }
    .td-fecha  { padding: 8px 12px; color: #6b7a8d; white-space: nowrap; }
    .td-desc   { padding: 8px 12px; color: #1a2332; }
    .td-monto  { padding: 8px 12px; text-align: right; font-weight: 600; white-space: nowrap; }
    .th-monto  { text-align: right; }

    .gastos-total-row {
      display: flex; justify-content: space-between; align-items: center;
      padding: 14px 18px; margin-top: 8px;
      background: #ede9fe; border-radius: 10px;
      font-weight: 700;
    }
    .gastos-total-label { font-size: 14px; color: #4c1d95; }
    .gastos-total-valor { font-size: 17px; }
  `]
})
export class SugerenciasComponent implements OnInit {
  sugerencias: Sugerencia[] = [];
  conciliacion: Conciliacion | null = null;
  loading = true;
  idConciliacion = 0;
  aceptandoLote = false;
  subiendoAuxiliar = false;
  reprocesando = false;
  columns = ['select', 'confianza', 'criterio', 'movBancario', 'movContable', 'estado', 'acciones'];

  selection = new SelectionModel<Sugerencia>(true, []);

  // Cruces manuales completados
  partidasCruzadas: any[] = [];
  mostrarCruces = false;

  // Gastos bancarios agrupados
  mostrarGastos = false;
  cargandoGastos = false;
  gruposGastos: GrupoGastoBancario[] = [];
  totalGastos = 0;
  private gastosYaCargados = false;
  private expandedGrupos = new Set<string>();

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
    this.cargarPartidasCruzadas();
  }

  cargarPartidasCruzadas(): void {
    this.api.listarPartidas(this.idConciliacion).subscribe({
      next: res => {
        this.partidasCruzadas = (res.data as any[]).filter(
          p => p.estado === 'CRUZADA' && !p.justificacion?.startsWith('INCOMPLETO|')
        );
      }
    });
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
        this.selection.deselect(sugerencia);
        this.snackBar.open('Sugerencia aceptada', 'Cerrar', { duration: 3000, panelClass: 'snack-success' });
      }
    });
  }

  rechazar(sugerencia: Sugerencia): void {
    this.api.rechazarSugerencia(this.idConciliacion, sugerencia.id).subscribe({
      next: res => {
        sugerencia.estado = res.data.estado;
        this.selection.deselect(sugerencia);
        this.snackBar.open('Sugerencia rechazada', 'Cerrar', { duration: 3000 });
      }
    });
  }

  aceptarSeleccionadas(): void {
    const ids = this.selection.selected.map(s => s.id);
    if (!ids.length) return;
    this.aceptandoLote = true;
    this.api.aceptarSugerenciasLote(this.idConciliacion, ids).subscribe({
      next: res => {
        res.data.forEach(updated => {
          const s = this.sugerencias.find(x => x.id === updated.id);
          if (s) s.estado = updated.estado;
        });
        this.selection.clear();
        this.aceptandoLote = false;
        this.snackBar.open(`${res.data.length} sugerencia(s) aceptadas`, 'Cerrar',
          { duration: 3000, panelClass: 'snack-success' });
      },
      error: () => { this.aceptandoLote = false; }
    });
  }

  reprocesar(): void {
    if (this.reprocesando) return;
    this.reprocesando = true;
    this.api.reprocesarMotor(this.idConciliacion).subscribe({
      next: res => {
        this.snackBar.open('Motor iniciado. Cargando sugerencias...', 'Cerrar', { duration: 4000 });
        this.pollJobAndReload(res.data);
      },
      error: err => {
        this.reprocesando = false;
        this.snackBar.open(err.error?.message ?? 'Error al reprocesar', 'Cerrar', { duration: 4000 });
      }
    });
  }

  private pollJobAndReload(jobId: string): void {
    const poll = () => {
      this.api.jobStatus(jobId).subscribe({
        next: res => {
          if (res.data.estado === 'COMPLETED' || res.data.estado === 'FAILED') {
            this.reprocesando = false;
            if (res.data.estado === 'COMPLETED') {
              this.api.obtenerConciliacion(this.idConciliacion).subscribe(r => { this.conciliacion = r.data; });
              this.cargarSugerencias();
            } else {
              this.snackBar.open('Error en el motor: ' + (res.data.mensajeError ?? ''), 'Cerrar', { duration: 5000 });
            }
          } else {
            setTimeout(poll, 1500);
          }
        },
        error: () => { this.reprocesando = false; }
      });
    };
    setTimeout(poll, 1500);
  }

  onAuxiliarChange(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file || this.subiendoAuxiliar) return;
    this.subiendoAuxiliar = true;
    this.api.cargarAuxiliar(this.idConciliacion, file).subscribe({
      next: res => {
        this.subiendoAuxiliar = false;
        const resumen = res.data;
        const partes = [`${resumen.nuevos} movimiento(s) nuevo(s)`];
        if (resumen.anulados > 0) {
          partes.push(`${resumen.anulados} anulado(s)` +
            (resumen.revertidos > 0 ? ` (${resumen.revertidos} conciliación(es) revertida(s))` : ''));
        }
        this.snackBar.open(`Auxiliar cargado: ${partes.join(', ')}.`, 'Cerrar', { duration: 5000 });

        if (resumen.jobId) {
          this.pollJobAndReload(resumen.jobId);
        } else {
          // Sin renglones nuevos que enviar al motor — igual puede haber anulados
          // que cambiaron el estado de sugerencias/movimientos; refrescar la vista.
          this.api.obtenerConciliacion(this.idConciliacion).subscribe(r => { this.conciliacion = r.data; });
          this.cargarSugerencias();
        }
      },
      error: err => {
        this.subiendoAuxiliar = false;
        this.snackBar.open(err.error?.message ?? 'Error al cargar auxiliar', 'Cerrar', { duration: 4000 });
      }
    });
  }

  getPendientesRows(): Sugerencia[] {
    return this.sugerencias.filter(s => s.estado === 'PENDIENTE_REVISION');
  }

  isAllPendientesSelected(): boolean {
    const pendientes = this.getPendientesRows();
    return pendientes.length > 0 && pendientes.every(s => this.selection.isSelected(s));
  }

  toggleAllPendientes(checked: boolean): void {
    if (checked) {
      this.getPendientesRows().forEach(s => this.selection.select(s));
    } else {
      this.selection.clear();
    }
  }

  pasarARevision(): void {
    this.api.pasarARevision(this.idConciliacion).subscribe({
      next: res => {
        this.conciliacion = res.data;
        this.snackBar.open('Conciliación pasada a revisión', 'Cerrar', { duration: 3000, panelClass: 'snack-success' });
      }
    });
  }

  canReview(): boolean { return this.auth.hasRole('CONTADOR', 'ADMIN'); }
  canClose(): boolean { return this.auth.hasRole('CONTADOR'); }

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

  // ── Gastos bancarios agrupados ─────────────────────────────────────────────

  toggleGastos(): void {
    this.mostrarGastos = !this.mostrarGastos;
    if (this.mostrarGastos && !this.gastosYaCargados) {
      this.cargarGastos();
    }
  }

  cargarGastos(): void {
    this.cargandoGastos = true;
    this.api.listarGastosAgrupadosConciliacion(this.idConciliacion).subscribe({
      next: res => {
        this.gastosYaCargados = true;
        this.cargandoGastos = false;
        this.gruposGastos = this.agruparPorDescripcion(res.data);
        this.totalGastos = this.gruposGastos.reduce((acc, g) => acc + g.total, 0);
      },
      error: () => { this.cargandoGastos = false; }
    });
  }

  private agruparPorDescripcion(movimientos: MovimientoAgrupado[]): GrupoGastoBancario[] {
    const map = new Map<string, GrupoGastoBancario>();
    for (const m of movimientos) {
      const key = m.descripcion;
      if (!map.has(key)) {
        map.set(key, { descripcion: key, count: 0, total: 0, tipo: m.tipo, movimientos: [] });
      }
      const g = map.get(key)!;
      g.count++;
      g.total += m.monto;
      g.movimientos.push(m);
    }
    return Array.from(map.values()).sort((a, b) => b.total - a.total);
  }

  toggleGrupo(descripcion: string): void {
    if (this.expandedGrupos.has(descripcion)) {
      this.expandedGrupos.delete(descripcion);
    } else {
      this.expandedGrupos.add(descripcion);
    }
  }

  isGrupoExpanded(descripcion: string): boolean {
    return this.expandedGrupos.has(descripcion);
  }

  // ── Stats ──────────────────────────────────────────────────────────────────

  getPendientes(): number { return this.sugerencias.filter(s => s.estado === 'PENDIENTE_REVISION').length; }
  getAceptadas(): number { return this.sugerencias.filter(s => s.estado === 'ACEPTADA').length; }
  getRechazadas(): number { return this.sugerencias.filter(s => s.estado === 'RECHAZADA').length; }

  getConfianzaClass(c: number): string {
    if (c >= 0.85) return 'conf-high';
    if (c >= 0.70) return 'conf-mid';
    return 'conf-low';
  }

  getCriterioClass(criterio: string): string {
    const map: Record<string, string> = {
      'MONTO_EXACTO': 'crit-exacto', 'MONTO_FECHA_PROXIMA': 'crit-fecha', 'MONTO_APROXIMADO': 'crit-aprox'
    };
    return map[criterio] ?? '';
  }

  getCriterioLabel(criterio: string): string {
    const map: Record<string, string> = {
      'MONTO_EXACTO': 'Monto Exacto', 'MONTO_FECHA_PROXIMA': 'Fecha Próxima', 'MONTO_APROXIMADO': 'Monto Aprox.'
    };
    return map[criterio] ?? criterio;
  }

  getSugerenciaEstadoClass(estado: string): string {
    const map: Record<string, string> = {
      'PENDIENTE_REVISION': 'sug-pendiente', 'ACEPTADA': 'sug-aceptada',
      'RECHAZADA': 'sug-rechazada', 'REASIGNADA': 'sug-reasignada'
    };
    return map[estado] ?? '';
  }

  getSugerenciaEstadoLabel(estado: string): string {
    const map: Record<string, string> = {
      'PENDIENTE_REVISION': 'Pendiente', 'ACEPTADA': 'Aceptada',
      'RECHAZADA': 'Rechazada', 'REASIGNADA': 'Reasignada'
    };
    return map[estado] ?? estado;
  }

  getTipoClass(tipo: string): string { return tipo === 'DEBITO' ? 'tipo-debito' : 'tipo-credito'; }

  getRowClass(estado: string): string {
    if (estado === 'ACEPTADA') return 'row-aceptada';
    if (estado === 'RECHAZADA') return 'row-rechazada';
    return '';
  }

  getEstadoClass(estado: string): string {
    const map: Record<string, string> = {
      'BORRADOR': 'estado-borrador', 'EN_REVISION': 'estado-revision', 'CERRADA': 'estado-cerrada'
    };
    return map[estado] ?? '';
  }

  getEstadoLabel(estado: string): string {
    const map: Record<string, string> = {
      'BORRADOR': 'Borrador', 'EN_REVISION': 'En Revisión', 'CERRADA': 'Cerrada'
    };
    return map[estado] ?? estado;
  }
}
