import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { MovimientoLeido, ResultadoLectura } from '../../core/models';

const TAMANO_MAXIMO = 10 * 1024 * 1024; // igual al límite de subida del backend

/**
 * Subida de un extracto PDF sin configuración. El backend lo carga solo cuando puede verificarlo;
 * el usuario únicamente interviene si el cuadre no se puede comprobar.
 */
@Component({
  selector: 'app-lectura-ia',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  template: `
    <div class="aviso">
      <mat-icon>privacy_tip</mat-icon>
      <span>
        Suba el extracto en PDF, sin configurar nada. Si cuadra con sus saldos, se carga y se concilia solo.
        Con la lectura con IA activa, el PDF se envía a un servicio externo y cada lectura tiene costo.
      </span>
    </div>

    <div class="aviso" *ngIf="bloqueado && !resultado">
      <mat-icon>info_outline</mat-icon>
      <span>No se puede subir otro PDF en este momento: ya hay un extracto cargado o en proceso.</span>
    </div>

    <div class="selector">
      <button mat-stroked-button type="button" (click)="entrada.click()" [disabled]="leyendo || bloqueado">
        <mat-icon>attach_file</mat-icon> Seleccionar PDF
      </button>
      <span class="nombre">{{ archivo ? archivo.name : 'Ningún archivo seleccionado' }}</span>
      <input #entrada type="file" accept=".pdf,application/pdf" hidden
             (change)="onArchivo($event)">
      <button mat-flat-button class="leer" type="button" (click)="subir()"
              [disabled]="!archivo || leyendo || confirmando || bloqueado">
        <mat-spinner diameter="18" *ngIf="leyendo"></mat-spinner>
        <span *ngIf="!leyendo">Subir y conciliar</span>
      </button>
    </div>
    <p class="nota" *ngIf="leyendo">Leyendo y verificando el extracto. Puede tardar varios minutos; no cierre esta página.</p>

    <div class="error" *ngIf="error">
      <mat-icon>error_outline</mat-icon>{{ error }}
    </div>

    <section *ngIf="resultado as r">
      <div class="estado" [ngClass]="'estado-' + r.estado" role="status">{{ r.mensaje }}</div>

      <ul class="lista errores" *ngIf="r.revision.errores.length">
        <li *ngFor="let e of r.revision.errores.slice(0, 10)">{{ e }}</li>
        <li *ngIf="r.revision.errores.length > 10">y {{ r.revision.errores.length - 10 }} más</li>
      </ul>
      <ul class="lista advertencias" *ngIf="r.revision.advertencias.length">
        <li *ngFor="let a of r.revision.advertencias">{{ a }}</li>
      </ul>

      <dl class="resumen">
        <div><dt>Producto</dt><dd>{{ etiquetaProducto() }}</dd></div>
        <div><dt>Movimientos leídos</dt><dd>{{ movimientos().length }}</dd></div>
        <div><dt>Saldo anterior</dt><dd>{{ valor(r.revision.lectura.saldoAnterior) }}</dd></div>
        <div><dt>Suma créditos</dt><dd>{{ valor(r.revision.sumaCreditos) }}</dd></div>
        <div><dt>Suma débitos</dt><dd>{{ valor(r.revision.sumaDebitos) }}</dd></div>
        <div><dt>Saldo final del extracto</dt><dd>{{ valor(r.revision.lectura.saldoFinal) }}</dd></div>
        <div><dt>Diferencia</dt><dd>{{ valor(r.revision.diferencia) }}</dd></div>
      </dl>

      <details *ngIf="movimientos().length" (toggle)="alternarDetalle($event)">
        <summary>Ver movimientos leídos (opcional)</summary>
        <div class="tabla" *ngIf="detalleAbierto">
          <table>
            <thead>
              <tr><th>Fecha</th><th>Descripción</th><th>Tipo</th><th class="num">Monto</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let m of movimientos()">
                <td>{{ m.fecha }}</td>
                <td>{{ m.descripcion }}</td>
                <td>{{ m.tipo }}</td>
                <td class="num">{{ valor(m.monto) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>

      <div class="acciones" *ngIf="r.estado === 'REQUIERE_CONFIRMACION'">
        <button mat-flat-button class="aprobar" type="button" (click)="confirmar()" [disabled]="!puedeConfirmar()">
          <mat-spinner diameter="18" *ngIf="confirmando"></mat-spinner>
          <span *ngIf="!confirmando">Cargar de todas formas</span>
        </button>
        <button mat-button type="button" (click)="descartar()" [disabled]="confirmando">Descartar</button>
      </div>
      <p class="nota" *ngIf="r.estado === 'REQUIERE_CONFIRMACION'">
        {{ acumula
            ? 'Los movimientos se agregarán a los extractos ya cargados.'
            : 'Los movimientos reemplazarán el extracto anterior que no esté conciliado.' }}
      </p>
    </section>
  `,
  styles: [`
    :host { display: block; margin: 16px 0; }
    .aviso, .error {
      display: flex; align-items: center; gap: 8px; border-radius: 8px;
      padding: 12px 16px; margin-bottom: 12px; font-size: 13px;
    }
    .aviso { background: #f0f7ff; border: 1px solid #bfdbfe; color: #1e40af; }
    .error { background: #fef2f2; border: 1px solid #fecaca; color: #b91c1c; }
    .selector { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
    .nombre { font-size: 13px; color: #6b7a8d; flex: 1; }
    .leer, .aprobar { background: #1a2332 !important; color: #fff !important; }
    .leer:disabled, .aprobar:disabled { opacity: 0.45; }
    .nota { font-size: 13px; color: #6b7a8d; }
    .estado { border-radius: 8px; padding: 12px 16px; margin: 16px 0 12px; font-size: 14px; font-weight: 600; }
    .estado-CARGADO { background: #f0fdf4; color: #166534; }
    .estado-RECHAZADO { background: #fef2f2; color: #b91c1c; }
    .estado-REQUIERE_CONFIRMACION { background: #fffbeb; color: #92400e; }
    summary { cursor: pointer; font-size: 13px; color: #3d7ebf; margin-bottom: 8px; }
    .lista { border-radius: 8px; padding: 10px 14px 10px 32px; font-size: 13px; }
    .errores { background: #fef2f2; color: #b91c1c; }
    .advertencias { background: #fffbeb; color: #92400e; }
    .resumen { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 8px; }
    .resumen dt { font-size: 12px; color: #6b7a8d; }
    .resumen dd { margin: 0; font-size: 14px; font-weight: 600; color: #1a2332; }
    .tabla { max-height: 320px; overflow: auto; border: 1px solid #e5e7eb; border-radius: 8px; margin-bottom: 12px; }
    table { width: 100%; border-collapse: collapse; font-size: 13px; }
    th, td { padding: 6px 12px; border-top: 1px solid #e5e7eb; text-align: left; }
    .num { text-align: right; white-space: nowrap; }
    .acciones { display: flex; gap: 12px; align-items: center; }
  `]
})
export class LecturaIaComponent implements OnDestroy {
  @Input({ required: true }) idConciliacion!: number;
  /** Tarjetas con auxiliar conjunto: cada extracto se suma a los anteriores. */
  @Input() acumula = false;
  @Input() bloqueado = false;
  /** Emite el id del job del motor cuando el extracto quedó guardado. */
  @Output() aprobado = new EventEmitter<string>();

  archivo: File | null = null;
  resultado: ResultadoLectura | null = null;
  detalleAbierto = false;
  leyendo = false;
  confirmando = false;
  error = '';
  private lecturaSub?: Subscription;

  constructor(private api: ApiService) {}

  onArchivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const elegido = input.files?.[0] ?? null;
    // Permite volver a elegir el mismo archivo
    input.value = '';
    if (elegido) this.seleccionar(elegido);
  }

  seleccionar(archivo: File): void {
    this.descartar();
    this.archivo = null;
    if (!archivo.name.toLowerCase().endsWith('.pdf')) {
      this.error = 'Seleccione un archivo PDF';
    } else if (archivo.size > TAMANO_MAXIMO) {
      this.error = 'El PDF supera el límite de 10 MB';
    } else {
      this.archivo = archivo;
    }
  }

  subir(): void {
    if (!this.archivo || this.leyendo || this.bloqueado) return;
    this.descartar();
    this.leyendo = true;
    this.lecturaSub = this.api.procesarExtractoIa(this.idConciliacion, this.archivo).subscribe({
      next: res => {
        this.leyendo = false;
        this.resultado = res.data;
        if (res.data.estado === 'CARGADO' && res.data.jobId) {
          this.archivo = null;
          this.aprobado.emit(res.data.jobId);
        }
      },
      error: err => {
        this.leyendo = false;
        this.error = err.error?.message ?? 'No se pudo leer el extracto';
      }
    });
  }

  puedeConfirmar(): boolean {
    return this.resultado?.estado === 'REQUIERE_CONFIRMACION' && !this.confirmando && !this.bloqueado;
  }

  confirmar(): void {
    if (!this.puedeConfirmar() || !this.resultado) return;
    this.confirmando = true;
    this.error = '';
    // El botón es la confirmación explícita de que se carga sin verificación completa
    this.api.confirmarExtractoIa(this.idConciliacion, this.resultado.revision.lectura, true).subscribe({
      next: res => {
        this.confirmando = false;
        // Se limpia el resultado para que no se pueda cargar dos veces
        this.archivo = null;
        this.descartar();
        this.aprobado.emit(res.data);
      },
      error: err => {
        this.confirmando = false;
        this.error = err.error?.message ?? 'No se pudo guardar el extracto';
      }
    });
  }

  descartar(): void {
    this.resultado = null;
    this.detalleAbierto = false;
    this.error = '';
  }

  alternarDetalle(event: Event): void {
    this.detalleAbierto = (event.target as HTMLDetailsElement).open;
  }

  movimientos(): MovimientoLeido[] {
    return this.resultado?.revision.lectura.movimientos ?? [];
  }

  etiquetaProducto(): string {
    const lectura = this.resultado?.revision.lectura;
    if (lectura?.tipoProducto === 'TARJETA_CREDITO') {
      return 'Tarjeta de crédito' +
        (lectura.ultimosDigitosTarjeta ? ' •••• ' + lectura.ultimosDigitosTarjeta : '');
    }
    return lectura?.tipoProducto === 'CUENTA' ? 'Cuenta bancaria' : 'No identificado';
  }

  valor(numero: number | null | undefined): string {
    return numero == null
      ? '—'
      : numero.toLocaleString('es-CO', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  ngOnDestroy(): void {
    this.lecturaSub?.unsubscribe();
  }
}
