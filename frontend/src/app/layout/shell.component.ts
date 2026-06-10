import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatSidenavModule, MatListModule,
    MatIconModule, MatToolbarModule, MatButtonModule, MatDividerModule
  ],
  template: `
    <mat-sidenav-container class="shell-container">
      <mat-sidenav mode="side" opened class="sidebar">
        <div class="sidebar-header">
          <div class="brand-logo">CB</div>
          <div class="brand-text">
            <span class="brand-title">Conciliación</span>
            <span class="brand-subtitle">Bancaria</span>
          </div>
        </div>

        <mat-divider></mat-divider>

        <div class="user-info">
          <div class="user-avatar">{{ getInitial() }}</div>
          <div class="user-details">
            <span class="user-email">{{ auth.getEmail() }}</span>
            <span class="user-role">{{ auth.getRol() }}</span>
          </div>
        </div>

        <mat-divider></mat-divider>

        <mat-nav-list class="nav-list">
          <a mat-list-item routerLink="/dashboard" routerLinkActive="active-link">
            <mat-icon matListItemIcon>dashboard</mat-icon>
            <span matListItemTitle>Dashboard</span>
          </a>
          <a mat-list-item routerLink="/conciliaciones" routerLinkActive="active-link">
            <mat-icon matListItemIcon>account_balance</mat-icon>
            <span matListItemTitle>Conciliaciones</span>
          </a>
          <a mat-list-item routerLink="/nueva-conciliacion"
             routerLinkActive="active-link"
             *ngIf="canCreate()">
            <mat-icon matListItemIcon>add_circle_outline</mat-icon>
            <span matListItemTitle>Nueva Conciliación</span>
          </a>
          <a mat-list-item routerLink="/bancos" routerLinkActive="active-link"
             *ngIf="canManageBancos()">
            <mat-icon matListItemIcon>account_balance_wallet</mat-icon>
            <span matListItemTitle>Bancos</span>
          </a>
          <a mat-list-item routerLink="/configuracion-extracto" routerLinkActive="active-link"
             *ngIf="canManageBancos()">
            <mat-icon matListItemIcon>settings</mat-icon>
            <span matListItemTitle>Config. Extractos</span>
          </a>
        </mat-nav-list>

        <div class="sidebar-footer">
          <mat-divider></mat-divider>
          <button mat-button class="logout-btn" (click)="logout()">
            <mat-icon>logout</mat-icon>
            Cerrar sesión
          </button>
        </div>
      </mat-sidenav>

      <mat-sidenav-content class="main-content">
        <router-outlet></router-outlet>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .shell-container { height: 100vh; }

    .sidebar {
      width: 260px;
      background: #1a2332;
      color: #fff;
      display: flex;
      flex-direction: column;
    }

    .sidebar-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 24px 20px;
    }

    .brand-logo {
      width: 40px;
      height: 40px;
      background: #3d7ebf;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 14px;
      color: #fff;
      flex-shrink: 0;
    }

    .brand-title {
      display: block;
      font-size: 14px;
      font-weight: 600;
      color: #fff;
      line-height: 1.2;
    }

    .brand-subtitle {
      display: block;
      font-size: 12px;
      color: #8a9bb0;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 20px;
    }

    .user-avatar {
      width: 36px;
      height: 36px;
      background: #3d7ebf;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      font-size: 14px;
      flex-shrink: 0;
    }

    .user-email {
      display: block;
      font-size: 12px;
      color: #e0e6ed;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 160px;
    }

    .user-role {
      display: block;
      font-size: 11px;
      color: #8a9bb0;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .nav-list {
      flex: 1;
      padding: 8px 0;
    }

    .nav-list a {
      color: #b0bec5;
      border-radius: 0;
      margin: 2px 0;
      height: 48px;
    }

    .nav-list a:hover {
      background: rgba(61, 126, 191, 0.15);
      color: #fff;
    }

    .nav-list a.active-link {
      background: rgba(61, 126, 191, 0.25);
      color: #fff;
      border-left: 3px solid #3d7ebf;
    }

    .nav-list mat-icon {
      color: inherit;
    }

    .sidebar-footer {
      padding: 8px 0 16px;
    }

    .logout-btn {
      width: 100%;
      color: #8a9bb0;
      justify-content: flex-start;
      padding: 0 20px;
      height: 48px;
      gap: 12px;
    }

    .logout-btn:hover { color: #fff; }

    .main-content {
      background: #f4f6f9;
      min-height: 100vh;
    }

    mat-divider { border-color: rgba(255,255,255,0.08) !important; }
  `]
})
export class ShellComponent {
  constructor(public auth: AuthService, private router: Router) {}

  getInitial(): string {
    return (this.auth.getEmail() ?? 'U')[0].toUpperCase();
  }

  canCreate(): boolean {
    return this.auth.hasRole('CONTADOR', 'AUXILIAR', 'ADMIN');
  }

  canManageBancos(): boolean {
    return this.auth.hasRole('CONTADOR', 'ADMIN');
  }

  logout(): void {
    this.auth.logout();
  }
}
