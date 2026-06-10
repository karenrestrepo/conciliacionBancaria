import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'conciliaciones',
        loadComponent: () => import('./pages/conciliaciones/conciliaciones.component').then(m => m.ConciliacionesComponent)
      },
      {
        path: 'nueva-conciliacion',
        loadComponent: () => import('./pages/nueva-conciliacion/nueva-conciliacion.component').then(m => m.NuevaConciliacionComponent)
      },
      {
        path: 'nueva-conciliacion/:id',
        loadComponent: () => import('./pages/nueva-conciliacion/nueva-conciliacion.component').then(m => m.NuevaConciliacionComponent)
      },
      {
        path: 'sugerencias/:id',
        loadComponent: () => import('./pages/sugerencias/sugerencias.component').then(m => m.SugerenciasComponent)
      },
      {
        path: 'partidas/:id',
        loadComponent: () => import('./pages/partidas/partidas.component').then(m => m.PartidasComponent)
      },
      {
        path: 'bancos',
        loadComponent: () => import('./pages/bancos/bancos.component').then(m => m.BancosComponent)
      },
      {
        path: 'bancos/:idBanco/cuentas',
        loadComponent: () => import('./pages/cuentas/cuentas.component').then(m => m.CuentasComponent)
      },
      {
        path: 'configuracion-extracto',
        loadComponent: () => import('./pages/configuracion-extracto/configuracion-extracto.component').then(m => m.ConfiguracionExtractoComponent)
      },
      {
        path: 'configuracion-extracto/:idBanco',
        loadComponent: () => import('./pages/configuracion-extracto/configuracion-extracto.component').then(m => m.ConfiguracionExtractoComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
