import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../../core/api.service';
import { Permiso, RolUsuario, UsuarioCreateRequest, UsuarioResponse, UsuarioUpdateRequest } from '../../core/models';

const PERMISO_LABELS: Record<Permiso, string> = {
  VER_CONCILIACIONES:  'Ver conciliaciones',
  CREAR_CONCILIACION:  'Crear conciliación',
  APROBAR_CONCILIACION:'Aprobar conciliación',
  CERRAR_CONCILIACION: 'Cerrar conciliación',
  VER_MOVIMIENTOS:     'Ver movimientos',
  GESTIONAR_BANCOS:    'Gestionar bancos',
  GESTIONAR_EXTRACTOS: 'Gestionar extractos',
  CARGAR_ARCHIVOS:     'Cargar archivos',
  GESTIONAR_USUARIOS:  'Gestionar usuarios'
};

const PERMISOS_ASIGNABLES: Permiso[] = [
  'VER_CONCILIACIONES', 'CREAR_CONCILIACION', 'APROBAR_CONCILIACION',
  'CERRAR_CONCILIACION', 'VER_MOVIMIENTOS', 'GESTIONAR_BANCOS',
  'GESTIONAR_EXTRACTOS', 'CARGAR_ARCHIVOS'
];

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatTableModule, MatButtonModule, MatIconModule,
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, MatChipsModule, MatSlideToggleModule, MatProgressSpinnerModule,
    MatTooltipModule, MatDividerModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <h2 class="page-title">Gestión de Usuarios</h2>
          <p class="page-subtitle">Cree y administre los usuarios de su empresa</p>
        </div>
        <button mat-flat-button color="primary" class="new-btn" (click)="abrirFormulario()">
          <mat-icon>person_add</mat-icon>
          Nuevo usuario
        </button>
      </div>

      <!-- Tabla de usuarios -->
      <div class="table-card" *ngIf="!modalAbierto">
        <div class="loading-overlay" *ngIf="cargando">
          <mat-spinner diameter="40"></mat-spinner>
        </div>

        <table mat-table [dataSource]="usuarios" class="usuarios-table" *ngIf="!cargando">
          <ng-container matColumnDef="nombre">
            <th mat-header-cell *matHeaderCellDef>Nombre</th>
            <td mat-cell *matCellDef="let u">
              <div class="user-cell">
                <div class="avatar">{{ u.nombre[0].toUpperCase() }}</div>
                <div>
                  <div class="user-name">{{ u.nombre }}</div>
                  <div class="user-email">{{ u.email }}</div>
                </div>
              </div>
            </td>
          </ng-container>

          <ng-container matColumnDef="rol">
            <th mat-header-cell *matHeaderCellDef>Rol</th>
            <td mat-cell *matCellDef="let u">
              <span class="rol-badge" [class]="'rol-' + u.rol.toLowerCase()">{{ u.rol }}</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="permisos">
            <th mat-header-cell *matHeaderCellDef>Permisos</th>
            <td mat-cell *matCellDef="let u">
              <span class="permisos-count">{{ u.permisos?.length ?? 0 }} permisos</span>
            </td>
          </ng-container>

          <ng-container matColumnDef="activo">
            <th mat-header-cell *matHeaderCellDef>Estado</th>
            <td mat-cell *matCellDef="let u">
              <span class="estado-badge" [class.activo]="u.activo" [class.inactivo]="!u.activo">
                {{ u.activo ? 'Activo' : 'Inactivo' }}
              </span>
            </td>
          </ng-container>

          <ng-container matColumnDef="acciones">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let u">
              <button mat-icon-button matTooltip="Editar" (click)="abrirFormulario(u)">
                <mat-icon>edit</mat-icon>
              </button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columnas"></tr>
          <tr mat-row *matRowDef="let row; columns: columnas;"></tr>
        </table>

        <div class="empty-state" *ngIf="!cargando && usuarios.length === 0">
          <mat-icon>group_off</mat-icon>
          <p>No hay usuarios creados. Cree el primero haciendo clic en "Nuevo usuario".</p>
        </div>
      </div>

      <!-- Formulario de creación/edición -->
      <div class="form-card" *ngIf="modalAbierto">
        <div class="form-header">
          <h3>{{ editandoId ? 'Editar usuario' : 'Nuevo usuario' }}</h3>
          <button mat-icon-button (click)="cerrarFormulario()">
            <mat-icon>close</mat-icon>
          </button>
        </div>

        <mat-divider></mat-divider>

        <form [formGroup]="form" (ngSubmit)="guardar()" class="form-body">
          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Nombre completo</mat-label>
              <input matInput formControlName="nombre" placeholder="Juan Pérez">
              <mat-error>Requerido</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Correo electrónico</mat-label>
              <input matInput type="email" formControlName="email" placeholder="usuario@empresa.com">
              <mat-error>Correo inválido</mat-error>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>{{ editandoId ? 'Nueva contraseña (dejar vacío para no cambiar)' : 'Contraseña' }}</mat-label>
              <input matInput [type]="hidePass ? 'password' : 'text'" formControlName="password">
              <button mat-icon-button matSuffix type="button" (click)="hidePass = !hidePass">
                <mat-icon>{{ hidePass ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
              <mat-hint *ngIf="!editandoId">Mínimo 8 caracteres</mat-hint>
              <mat-error *ngIf="form.get('password')?.hasError('minlength')">Mínimo 8 caracteres</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Rol</mat-label>
              <mat-select formControlName="rol" (selectionChange)="onRolChange()">
                <mat-option value="AUXILIAR">Auxiliar</mat-option>
                <mat-option value="CONTADOR">Contador</mat-option>
                <mat-option value="FINANZAS">Finanzas</mat-option>
              </mat-select>
              <mat-error>Seleccione un rol</mat-error>
            </mat-form-field>
          </div>

          <mat-slide-toggle *ngIf="editandoId" formControlName="activo" color="primary" style="margin-bottom:16px">
            Usuario activo
          </mat-slide-toggle>

          <!-- Permisos -->
          <div class="permisos-section">
            <div class="permisos-header">
              <span class="permisos-title">Permisos</span>
              <div class="permisos-actions">
                <button mat-button type="button" (click)="cargarDefaultsRol()">
                  <mat-icon>refresh</mat-icon> Restablecer defaults del rol
                </button>
              </div>
            </div>

            <div class="permisos-hint">Los permisos marcados corresponden a los valores por defecto del rol seleccionado. Puede habilitarlos o quitarlos según necesite.</div>

            <div class="permisos-grid">
              <div *ngFor="let p of permisosAsignables" class="permiso-item">
                <mat-checkbox
                  [checked]="tienePermiso(p)"
                  (change)="togglePermiso(p, $event.checked)"
                  [class.default-permiso]="esDefaultDelRol(p)">
                  {{ permisoLabel(p) }}
                </mat-checkbox>
                <span class="default-tag" *ngIf="esDefaultDelRol(p)">default</span>
              </div>
            </div>
          </div>

          <div class="error-message" *ngIf="errorMsg">
            <mat-icon>error_outline</mat-icon>{{ errorMsg }}
          </div>

          <div class="form-actions">
            <button mat-stroked-button type="button" (click)="cerrarFormulario()">Cancelar</button>
            <button mat-flat-button color="primary" type="submit" [disabled]="guardando || form.invalid">
              <mat-spinner diameter="18" *ngIf="guardando"></mat-spinner>
              <span *ngIf="!guardando">{{ editandoId ? 'Guardar cambios' : 'Crear usuario' }}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .page-container {
      padding: 32px;
      max-width: 1100px;
      margin: 0 auto;
    }

    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      margin-bottom: 24px;
    }

    .page-title {
      font-size: 22px;
      font-weight: 600;
      color: #1a2332;
      margin: 0 0 4px;
    }

    .page-subtitle {
      font-size: 14px;
      color: #6b7a8d;
      margin: 0;
    }

    .new-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      height: 42px;
    }

    .new-btn:hover { background: #3d7ebf !important; }

    .table-card, .form-card {
      background: #fff;
      border-radius: 12px;
      box-shadow: 0 2px 12px rgba(0,0,0,0.06);
      overflow: hidden;
      position: relative;
    }

    .loading-overlay {
      display: flex;
      justify-content: center;
      padding: 60px;
    }

    .usuarios-table { width: 100%; }

    .user-cell {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 8px 0;
    }

    .avatar {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #3d7ebf;
      color: #fff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      font-size: 14px;
      flex-shrink: 0;
    }

    .user-name { font-weight: 500; font-size: 14px; color: #1a2332; }
    .user-email { font-size: 12px; color: #6b7a8d; }

    .rol-badge {
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .rol-auxiliar { background: #e3f2fd; color: #1565c0; }
    .rol-contador { background: #f3e5f5; color: #6a1b9a; }
    .rol-finanzas { background: #e8f5e9; color: #2e7d32; }

    .permisos-count { font-size: 13px; color: #6b7a8d; }

    .estado-badge {
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 12px;
      font-weight: 500;
    }

    .activo { background: #e8f5e9; color: #2e7d32; }
    .inactivo { background: #fce4ec; color: #c62828; }

    .empty-state {
      text-align: center;
      padding: 60px 24px;
      color: #8a9bb0;
    }

    .empty-state mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
      margin-bottom: 16px;
      opacity: 0.4;
    }

    /* Formulario */
    .form-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px 24px 16px;
    }

    .form-header h3 { margin: 0; font-size: 18px; font-weight: 600; color: #1a2332; }

    .form-body { padding: 20px 24px 24px; }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
      margin-bottom: 4px;
    }

    .full-width { width: 100%; }

    .permisos-section {
      background: #f8f9fb;
      border-radius: 8px;
      padding: 16px;
      margin: 16px 0;
    }

    .permisos-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 6px;
    }

    .permisos-title { font-weight: 600; font-size: 14px; color: #1a2332; }

    .permisos-hint {
      font-size: 12px;
      color: #8a9bb0;
      margin-bottom: 14px;
      line-height: 1.5;
    }

    .permisos-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 10px;
    }

    .permiso-item {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .default-tag {
      font-size: 10px;
      padding: 2px 6px;
      background: #e8f0fe;
      color: #1a73e8;
      border-radius: 4px;
      font-weight: 600;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #e53935;
      font-size: 13px;
      margin-bottom: 12px;
      padding: 10px 12px;
      background: #ffeaea;
      border-radius: 6px;
    }

    .form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      margin-top: 8px;
    }

    th.mat-header-cell {
      font-weight: 600;
      color: #6b7a8d;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    td.mat-cell { padding: 6px 16px !important; }
    th.mat-header-cell { padding: 12px 16px !important; }
  `]
})
export class UsuariosComponent implements OnInit {
  columnas = ['nombre', 'rol', 'permisos', 'activo', 'acciones'];
  usuarios: UsuarioResponse[] = [];
  cargando = false;
  modalAbierto = false;
  editandoId: number | null = null;
  guardando = false;
  errorMsg = '';
  hidePass = true;

  permisosAsignables = PERMISOS_ASIGNABLES;
  permisosSeleccionados = new Set<Permiso>();
  defaultsDelRol = new Set<Permiso>();

  form!: FormGroup;

  constructor(private api: ApiService, private fb: FormBuilder) {}

  ngOnInit(): void {
    this.cargarUsuarios();
    this.initForm();
  }

  private initForm(u?: UsuarioResponse): void {
    this.form = this.fb.group({
      nombre: [u?.nombre ?? '', Validators.required],
      email: [u?.email ?? '', [Validators.required, Validators.email]],
      password: ['', u ? [] : [Validators.required, Validators.minLength(8)]],
      rol: [u?.rol ?? 'AUXILIAR', Validators.required],
      activo: [u?.activo ?? true]
    });

    this.permisosSeleccionados = new Set(u?.permisos ?? []);
    if (!u) {
      this.cargarDefaultsRol();
    }
  }

  cargarUsuarios(): void {
    this.cargando = true;
    this.api.listarUsuarios().subscribe({
      next: res => { this.usuarios = res.data ?? []; this.cargando = false; },
      error: () => { this.cargando = false; }
    });
  }

  abrirFormulario(u?: UsuarioResponse): void {
    this.editandoId = u?.id ?? null;
    this.errorMsg = '';
    this.initForm(u);
    if (u) {
      this.cargarDefaultsDelRol(u.rol);
    } else {
      this.cargarDefaultsDelRol('AUXILIAR');
    }
    this.modalAbierto = true;
  }

  cerrarFormulario(): void {
    this.modalAbierto = false;
    this.editandoId = null;
    this.errorMsg = '';
  }

  onRolChange(): void {
    const rol = this.form.get('rol')?.value as RolUsuario;
    this.cargarDefaultsDelRol(rol);
    // Al cambiar el rol, se aplican los defaults automáticamente
    this.cargarDefaultsRol();
  }

  cargarDefaultsDelRol(rol: RolUsuario): void {
    this.api.permisosDefaultsRol(rol).subscribe({
      next: res => { this.defaultsDelRol = new Set(res.data ?? []); },
      error: () => {}
    });
  }

  cargarDefaultsRol(): void {
    const rol = this.form.get('rol')?.value as RolUsuario;
    this.api.permisosDefaultsRol(rol).subscribe({
      next: res => {
        this.defaultsDelRol = new Set(res.data ?? []);
        this.permisosSeleccionados = new Set(res.data ?? []);
      },
      error: () => {}
    });
  }

  tienePermiso(p: Permiso): boolean {
    return this.permisosSeleccionados.has(p);
  }

  esDefaultDelRol(p: Permiso): boolean {
    return this.defaultsDelRol.has(p);
  }

  togglePermiso(p: Permiso, checked: boolean): void {
    checked ? this.permisosSeleccionados.add(p) : this.permisosSeleccionados.delete(p);
  }

  permisoLabel(p: Permiso): string {
    return PERMISO_LABELS[p] ?? p;
  }

  guardar(): void {
    if (this.form.invalid) return;
    this.guardando = true;
    this.errorMsg = '';

    const { nombre, email, password, rol, activo } = this.form.value;
    const permisos = Array.from(this.permisosSeleccionados);

    if (this.editandoId) {
      const req: UsuarioUpdateRequest = { nombre, email, rol, activo, permisos,
        ...(password ? { password } : {}) };
      this.api.actualizarUsuario(this.editandoId, req).subscribe({
        next: res => {
          const idx = this.usuarios.findIndex(u => u.id === this.editandoId);
          if (idx >= 0) this.usuarios[idx] = res.data;
          this.guardando = false;
          this.cerrarFormulario();
          this.cargarUsuarios();
        },
        error: err => {
          this.guardando = false;
          this.errorMsg = err?.error?.message ?? 'Error al guardar.';
        }
      });
    } else {
      const req: UsuarioCreateRequest = { nombre, email, password, rol, permisos };
      this.api.crearUsuario(req).subscribe({
        next: res => {
          this.usuarios = [...this.usuarios, res.data];
          this.guardando = false;
          this.cerrarFormulario();
        },
        error: err => {
          this.guardando = false;
          this.errorMsg = err?.error?.message ?? 'Error al crear usuario.';
        }
      });
    }
  }
}
