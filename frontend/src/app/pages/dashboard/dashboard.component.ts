import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { NgChartsModule } from 'ng2-charts';
import { ChartData, ChartOptions } from 'chart.js';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { MetricasResumen } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatCardModule, MatIconModule,
    MatButtonModule, MatProgressSpinnerModule, MatDividerModule, NgChartsModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h1 class="page-title">Dashboard</h1>
          <p class="page-subtitle">Resumen general del módulo de conciliación bancaria</p>
        </div>
        <button mat-flat-button routerLink="/nueva-conciliacion"
                *ngIf="canCreate()" class="action-btn">
          <mat-icon>add</mat-icon>
          Nueva Conciliación
        </button>
      </div>

      <div *ngIf="loading" class="loading-container">
        <mat-spinner diameter="40"></mat-spinner>
      </div>

      <ng-container *ngIf="!loading && metricas">
        <div class="kpi-grid">
          <mat-card class="kpi-card">
            <div class="kpi-icon-wrap blue">
              <mat-icon>account_balance</mat-icon>
            </div>
            <div class="kpi-info">
              <span class="kpi-value">{{ metricas.totalConciliaciones }}</span>
              <span class="kpi-label">Total Conciliaciones</span>
            </div>
          </mat-card>

          <mat-card class="kpi-card">
            <div class="kpi-icon-wrap amber">
              <mat-icon>pending</mat-icon>
            </div>
            <div class="kpi-info">
              <span class="kpi-value">{{ metricas.enBorrador }}</span>
              <span class="kpi-label">En Borrador</span>
            </div>
          </mat-card>

          <mat-card class="kpi-card">
            <div class="kpi-icon-wrap orange">
              <mat-icon>rate_review</mat-icon>
            </div>
            <div class="kpi-info">
              <span class="kpi-value">{{ metricas.enRevision }}</span>
              <span class="kpi-label">En Revisión</span>
            </div>
          </mat-card>

          <mat-card class="kpi-card">
            <div class="kpi-icon-wrap green">
              <mat-icon>check_circle</mat-icon>
            </div>
            <div class="kpi-info">
              <span class="kpi-value">{{ metricas.cerradas }}</span>
              <span class="kpi-label">Cerradas</span>
            </div>
          </mat-card>
        </div>

        <div class="charts-grid">
          <mat-card class="chart-card">
            <mat-card-header>
              <mat-card-title>Estado de Conciliaciones</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="chart-wrapper">
                <canvas baseChart
                  [data]="estadoChartData"
                  [options]="doughnutOptions"
                  type="doughnut">
                </canvas>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="chart-card">
            <mat-card-header>
              <mat-card-title>Sugerencias del Motor</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="chart-wrapper">
                <canvas baseChart
                  [data]="sugerenciasChartData"
                  [options]="barOptions"
                  type="bar">
                </canvas>
              </div>
            </mat-card-content>
          </mat-card>
        </div>

        <div class="stats-grid">
          <mat-card class="stat-card">
            <mat-card-header>
              <mat-card-title>Movimientos Procesados</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-row">
                <span class="stat-label">Bancarios</span>
                <span class="stat-value">{{ metricas.totalMovimientosBancarios }}</span>
              </div>
              <mat-divider></mat-divider>
              <div class="stat-row">
                <span class="stat-label">Contables</span>
                <span class="stat-value">{{ metricas.totalMovimientosContables }}</span>
              </div>
              <mat-divider></mat-divider>
              <div class="stat-row">
                <span class="stat-label">Diferencia Promedio</span>
                <span class="stat-value">{{ metricas.diferenciaPromedio | currency:'COP':'symbol':'1.0-0' }}</span>
              </div>
            </mat-card-content>
          </mat-card>

          <mat-card class="stat-card">
            <mat-card-header>
              <mat-card-title>Resumen de Sugerencias</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <div class="stat-row">
                <span class="stat-label">Total generadas</span>
                <span class="stat-value">{{ metricas.totalSugerencias }}</span>
              </div>
              <mat-divider></mat-divider>
              <div class="stat-row">
                <span class="stat-label">Aceptadas</span>
                <span class="stat-value green-text">{{ metricas.sugerenciasAceptadas }}</span>
              </div>
              <mat-divider></mat-divider>
              <div class="stat-row">
                <span class="stat-label">Rechazadas</span>
                <span class="stat-value red-text">{{ metricas.sugerenciasRechazadas }}</span>
              </div>
              <mat-divider></mat-divider>
              <div class="stat-row">
                <span class="stat-label">Pendientes</span>
                <span class="stat-value amber-text">{{ metricas.sugerenciasPendientes }}</span>
              </div>
            </mat-card-content>
          </mat-card>
        </div>
      </ng-container>
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

    .page-subtitle {
      font-size: 14px;
      color: #6b7a8d;
      margin: 0;
    }

    .action-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 42px;
      gap: 6px;
    }

    .loading-container {
      display: flex;
      justify-content: center;
      padding: 80px;
    }

    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 20px;
      margin-bottom: 24px;
    }

    .kpi-card {
      display: flex !important;
      flex-direction: row !important;
      align-items: center;
      gap: 16px;
      padding: 20px !important;
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
    }

    .kpi-icon-wrap {
      width: 48px;
      height: 48px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .kpi-icon-wrap mat-icon { color: #fff; font-size: 22px; }
    .blue { background: #3d7ebf; }
    .amber { background: #f59e0b; }
    .orange { background: #f97316; }
    .green { background: #22c55e; }

    .kpi-value {
      display: block;
      font-size: 28px;
      font-weight: 700;
      color: #1a2332;
      line-height: 1;
    }

    .kpi-label {
      display: block;
      font-size: 12px;
      color: #6b7a8d;
      margin-top: 4px;
    }

    .charts-grid {
      display: grid;
      grid-template-columns: 1fr 2fr;
      gap: 20px;
      margin-bottom: 24px;
    }

    .chart-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
    }

    .chart-wrapper {
      height: 240px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 16px 0;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
    }

    .stat-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
    }

    .stat-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 14px 0;
    }

    .stat-label { font-size: 14px; color: #6b7a8d; }
    .stat-value { font-size: 16px; font-weight: 600; color: #1a2332; }
    .green-text { color: #22c55e !important; }
    .red-text { color: #e53935 !important; }
    .amber-text { color: #f59e0b !important; }

    mat-card-title { font-size: 15px !important; font-weight: 600 !important; color: #1a2332 !important; }
  `]
})
export class DashboardComponent implements OnInit {
  metricas: MetricasResumen | null = null;
  loading = true;

  estadoChartData: ChartData<'doughnut'> = {
    labels: ['Borrador', 'En Revisión', 'Cerradas'],
    datasets: [{
      data: [0, 0, 0],
      backgroundColor: ['#f59e0b', '#f97316', '#22c55e'],
      borderWidth: 0
    }]
  };

  sugerenciasChartData: ChartData<'bar'> = {
    labels: ['Aceptadas', 'Rechazadas', 'Pendientes'],
    datasets: [{
      label: 'Sugerencias',
      data: [0, 0, 0],
      backgroundColor: ['#22c55e', '#e53935', '#f59e0b'],
      borderRadius: 6,
      borderSkipped: false
    }]
  };

  doughnutOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom' } }
  };

  barOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
  };

  constructor(private api: ApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.api.obtenerMetricas().subscribe({
      next: res => {
        this.metricas = res.data;
        this.estadoChartData.datasets[0].data = [
          res.data.enBorrador, res.data.enRevision, res.data.cerradas
        ];
        this.sugerenciasChartData.datasets[0].data = [
          res.data.sugerenciasAceptadas,
          res.data.sugerenciasRechazadas,
          res.data.sugerenciasPendientes
        ];
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  canCreate(): boolean {
    return this.auth.hasRole('CONTADOR', 'AUXILIAR', 'ADMIN');
  }
}
