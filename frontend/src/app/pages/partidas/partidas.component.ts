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
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'app-partidas',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, MatCardModule,
    MatTableModule, MatIconModule, MatButtonModule, MatChipsModule,
    MatFormFieldModule, MatInputModule, MatDatepickerModule,
    MatNativeDateModule, MatSnackBarModule, MatProgressSpinnerModule,
    MatDialogModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Partidas No Conciliadas</h1>
          <p class="page-subtitle">Conciliación #{{ idConciliacion }} — Justifique cada partida para poder cerrar</p>
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
        </div>

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
                <span class="estado-badge" [class.pendiente]="partida.estado === 'PENDIENTE'"
                                            [class.justificada]="partida.estado === 'JUSTIFICADA'">
                  {{ partida.estado === 'PENDIENTE' ? 'Pendiente' : 'Justificada' }}
                </span>
              </div>

              <div *ngIf="partida.estado === 'JUSTIFICADA'" class="justificacion-actual">
                <mat-icon>check_circle</mat-icon>
                <div>
                  <p class="just-texto">{{ partida.justificacion }}</p>
                  <p class="just-fecha">{{ partida.fechaJustificacion | date:'dd/MM/yyyy' }}</p>
                </div>
              </div>

              <form *ngIf="partida.estado === 'PENDIENTE'"
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
            </mat-card-content>
          </mat-card>
        </div>

        <div *ngIf="partidas.length === 0" class="empty-state">
          <mat-icon>check_circle</mat-icon>
          <p>No hay partidas para esta conciliación</p>
        </div>
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
    .summary-row { display: flex; gap: 12px; margin-bottom: 20px; }
    .count-badge { padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 500; }
    .count-badge.pending { background: #fef3c7; color: #92400e; }
    .count-badge.justified { background: #d1fae5; color: #065f46; }
    .partida-card { margin-bottom: 16px; }
    .partida-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .partida-meta { display: flex; align-items: center; gap: 12px; }
    .origen-badge { display: flex; align-items: center; gap: 6px; padding: 4px 12px; border-radius: 6px; font-size: 13px; font-weight: 500; }
    .origen-badge mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .origen-badge.bancario { background: #dbeafe; color: #1e40af; }
    .origen-badge.contable { background: #ede9fe; color: #5b21b6; }
    .partida-id { font-size: 12px; color: #9ca3af; }
    .estado-badge { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
    .estado-badge.pendiente { background: #fef3c7; color: #b45309; }
    .estado-badge.justificada { background: #d1fae5; color: #065f46; }
    .justificacion-actual { display: flex; align-items: flex-start; gap: 10px; background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 8px; padding: 12px; }
    .justificacion-actual mat-icon { color: #16a34a; margin-top: 2px; }
    .just-texto { margin: 0 0 4px; font-size: 14px; color: #1a2332; }
    .just-fecha { margin: 0; font-size: 12px; color: #6b7a8d; }
    .justificar-form { display: flex; flex-direction: column; gap: 12px; }
    .field-texto { width: 100%; }
    .form-row { display: flex; gap: 12px; align-items: center; }
    .field-fecha { width: 200px; }
    .guardar-btn { background: #1a2332 !important; color: #fff !important; border-radius: 8px !important; height: 42px; }
    .empty-state { text-align: center; padding: 60px; color: #9ca3af; }
    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; display: block; margin: 0 auto 12px; color: #22c55e; }
  `]
})
export class PartidasComponent implements OnInit {
  idConciliacion!: number;
  partidas: any[] = [];
  loading = true;
  saving: Record<number, boolean> = {};
  private forms: Record<number, FormGroup> = {};

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
            this.forms[p.id] = this.fb.group({
              justificacion: ['', Validators.required],
              fecha: [new Date(), Validators.required]
            });
          }
        });
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  getForm(id: number): FormGroup {
    return this.forms[id];
  }

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

  getPendientes(): number { return this.partidas.filter(p => p.estado === 'PENDIENTE').length; }
  getJustificadas(): number { return this.partidas.filter(p => p.estado === 'JUSTIFICADA').length; }
}
