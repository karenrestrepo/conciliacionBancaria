import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatCheckboxModule, MatCheckboxChange } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { FiltrosReportePendientes } from './reporte-pendientes.util';

/**
 * Default de "Incluir pendientes de meses anteriores": desmarcado, para que el reporte
 * muestre solo el período actual salvo que el usuario pida explícitamente lo histórico.
 * Constante aislada para poder cambiar el default fácilmente si se decide lo contrario.
 */
export const DEFAULT_INCLUIR_HISTORICAS = false;

@Component({
  selector: 'app-reporte-pendientes-dialog',
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatCheckboxModule, MatButtonModule, MatDividerModule],
  template: `
    <h2 mat-dialog-title>Generar reporte de pendientes</h2>

    <mat-dialog-content class="contenido">
      <p class="grupo-label">Incluir en el reporte</p>
      <div class="grupo-checkboxes">
        <mat-checkbox [checked]="todos" (change)="onToggleTodos($event)">Todos</mat-checkbox>
        <mat-divider class="grupo-divider"></mat-divider>
        <mat-checkbox [checked]="debito" (change)="onToggleDetalle('debito', $event)">Débitos</mat-checkbox>
        <mat-checkbox [checked]="credito" (change)="onToggleDetalle('credito', $event)">Créditos</mat-checkbox>
        <mat-checkbox [checked]="bancario" (change)="onToggleDetalle('bancario', $event)">Pendientes bancarios</mat-checkbox>
        <mat-checkbox [checked]="contable" (change)="onToggleDetalle('contable', $event)">Pendientes contables</mat-checkbox>
      </div>

      <mat-divider class="seccion-divider"></mat-divider>

      <mat-checkbox [checked]="incluirHistoricas" (change)="incluirHistoricas = $event.checked">
        Incluir pendientes de meses anteriores
      </mat-checkbox>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-stroked-button (click)="cancelar()">Cancelar</button>
      <button mat-flat-button class="generar-btn" (click)="generar()">Generar</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .contenido { display: flex; flex-direction: column; gap: 4px; min-width: 320px; }
    .grupo-label { font-size: 13px; font-weight: 600; color: #374151; margin: 4px 0 8px; }
    .grupo-checkboxes { display: flex; flex-direction: column; gap: 10px; }
    .grupo-divider { margin: 2px 0 4px; }
    .seccion-divider { margin: 18px 0 14px; }
    .generar-btn { background: #5b21b6 !important; color: #fff !important; border-radius: 8px !important; }
  `],
})
export class ReportePendientesDialogComponent {
  debito = true;
  credito = true;
  bancario = true;
  contable = true;
  /** Derivado de los 4 anteriores, pero mantenido como estado propio para el checkbox "Todos". */
  todos = true;
  incluirHistoricas = DEFAULT_INCLUIR_HISTORICAS;

  constructor(private dialogRef: MatDialogRef<ReportePendientesDialogComponent, FiltrosReportePendientes>) {}

  onToggleTodos(event: MatCheckboxChange): void {
    this.todos = event.checked;
    this.debito = this.credito = this.bancario = this.contable = event.checked;
  }

  /**
   * "Todos" es un atajo que marca las 4 casillas de detalle; si el usuario desmarca una
   * individual después, "Todos" se desmarca automáticamente. Si vuelve a marcar las 4 a
   * mano, "Todos" refleja que quedó completo -- comportamiento típico "seleccionar todos".
   */
  onToggleDetalle(campo: 'debito' | 'credito' | 'bancario' | 'contable', event: MatCheckboxChange): void {
    this[campo] = event.checked;
    this.todos = this.debito && this.credito && this.bancario && this.contable;
  }

  cancelar(): void {
    this.dialogRef.close();
  }

  generar(): void {
    const filtros: FiltrosReportePendientes = {
      debito: this.debito,
      credito: this.credito,
      bancario: this.bancario,
      contable: this.contable,
      incluirHistoricas: this.incluirHistoricas,
    };
    this.dialogRef.close(filtros);
  }
}
