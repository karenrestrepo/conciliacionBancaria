import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatStepperModule, MatStepper } from '@angular/material/stepper';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import {
  Banco, Cuenta, ConfiguracionExtracto, TipoArchivoExtracto,
  TipoOrigenExtracto, ConvencionSigno, CampoAnchoFijo,
  ConfiguracionExtractoDetalle, PruebaConfiguracionResultado
} from '../../core/models';

const CAMPOS_ANCHO_FIJO: { valor: CampoAnchoFijo; etiqueta: string }[] = [
  { valor: 'dia', etiqueta: 'Día' },
  { valor: 'mes', etiqueta: 'Mes' },
  { valor: 'anio', etiqueta: 'Año' },
  { valor: 'fecha', etiqueta: 'Fecha completa' },
  { valor: 'descripcion', etiqueta: 'Descripción' },
  { valor: 'monto', etiqueta: 'Monto' },
  { valor: 'debito', etiqueta: 'Débito' },
  { valor: 'credito', etiqueta: 'Crédito' },
  { valor: 'signo', etiqueta: 'Signo (+/-)' },
  { valor: 'tipo', etiqueta: 'Tipo (DEBITO/CREDITO)' },
];

@Component({
  selector: 'app-configuracion-extracto',
  standalone: true,
  imports: [
    CommonModule, RouterModule, ReactiveFormsModule, FormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatStepperModule,
    MatCheckboxModule, MatTableModule, MatTooltipModule
  ],
  template: `
    <div class="page-container">
      <div class="page-header">
        <div>
          <button mat-button routerLink="/bancos" class="back-btn">
            <mat-icon>arrow_back</mat-icon>
            Bancos
          </button>
          <h1 class="page-title">Configuración de Extractos</h1>
          <p class="page-subtitle">Define cómo parsear archivos de extracto para cada banco</p>
        </div>
      </div>

      <!-- Stepper de configuración -->
      <mat-card class="stepper-card" *ngIf="canManage()">
        <mat-card-header>
          <mat-card-title>{{ editandoId ? 'Editar configuración' : 'Nueva configuración' }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-stepper [linear]="!editandoId" #stepper orientation="horizontal" class="stepper">

            <!-- PASO 1: Banco -->
            <mat-step [stepControl]="bancoForm" label="Banco">
              <form [formGroup]="bancoForm" class="step-form">
                <mat-form-field appearance="outline" class="full-field">
                  <mat-label>Banco</mat-label>
                  <mat-select formControlName="idBanco" (selectionChange)="onBancoChange($event.value)">
                    <mat-option *ngFor="let b of bancos" [value]="b.id">
                      {{ b.nombre }}{{ b.codigo ? ' (' + b.codigo + ')' : '' }}
                    </mat-option>
                  </mat-select>
                  <mat-error *ngIf="bancoForm.get('idBanco')?.hasError('required')">
                    Seleccione un banco
                  </mat-error>
                </mat-form-field>

                <div class="step-actions">
                  <button mat-flat-button class="next-btn" matStepperNext
                          [disabled]="bancoForm.invalid">
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 2: Nombre -->
            <mat-step [stepControl]="nombreForm" label="Nombre">
              <form [formGroup]="nombreForm" class="step-form">
                <mat-form-field appearance="outline" class="full-field">
                  <mat-label>Nombre de la configuración</mat-label>
                  <input matInput formControlName="nombre"
                         placeholder="Ej: Extracto mensual CSV">
                  <mat-error *ngIf="nombreForm.get('nombre')?.hasError('required')">
                    El nombre es obligatorio
                  </mat-error>
                  <mat-error *ngIf="nombreForm.get('nombre')?.hasError('maxlength')">
                    Máximo 200 caracteres
                  </mat-error>
                </mat-form-field>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="next-btn" matStepperNext
                          [disabled]="nombreForm.invalid">
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 3: Tipo de archivo -->
            <mat-step [stepControl]="tipoForm" label="Tipo archivo">
              <form [formGroup]="tipoForm" class="step-form">
                <mat-form-field appearance="outline" class="full-field">
                  <mat-label>Tipo de archivo</mat-label>
                  <mat-select formControlName="tipoArchivo" (selectionChange)="onTipoArchivoChange()">
                    <mat-option value="CSV">CSV</mat-option>
                    <mat-option value="TXT">TXT</mat-option>
                    <mat-option value="XLS">XLS</mat-option>
                    <mat-option value="XLSX">XLSX</mat-option>
                    <mat-option value="PDF" disabled matTooltip="Próximamente">PDF (aún no soportado)</mat-option>
                  </mat-select>
                  <mat-error *ngIf="tipoForm.get('tipoArchivo')?.hasError('required')">
                    Seleccione un tipo de archivo
                  </mat-error>
                </mat-form-field>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="next-btn" matStepperNext
                          [disabled]="tipoForm.invalid">
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 4: Detalle de configuración (omitir para PDF) -->
            <mat-step label="Detalle">
              <form [formGroup]="detalleForm" class="step-form">
                <div *ngIf="tipoArchivo === 'PDF'" class="pdf-notice">
                  <mat-icon>info_outline</mat-icon>
                  <span>Los archivos PDF aún no están soportados.</span>
                </div>

                <ng-container *ngIf="tipoArchivo === 'TXT'">
                  <mat-form-field appearance="outline" class="full-field">
                    <mat-label>Formato del TXT</mat-label>
                    <mat-select formControlName="tipoOrigen" (selectionChange)="onTipoOrigenChange()">
                      <mat-option value="DELIMITADO">Delimitado (columnas con separador, ej. CSV)</mat-option>
                      <mat-option value="ANCHO_FIJO">Ancho fijo — reporte de impresión (ej. Davivienda)</mat-option>
                    </mat-select>
                    <mat-hint>"Ancho fijo" define columnas por posición de caracter, sin escribir regex</mat-hint>
                  </mat-form-field>
                </ng-container>

                <!-- ── Modo ANCHO FIJO: columnas por posición, sin regex ─────────── -->
                <ng-container *ngIf="esTxtAnchoFijo()">
                  <div class="ancho-fijo-info">
                    <div class="info-header">
                      <mat-icon>info_outline</mat-icon>
                      <strong>Columnas por posición de caracter</strong>
                    </div>
                    <p class="info-desc">
                      Pegue abajo una línea real de ejemplo del extracto, luego defina para cada
                      columna en qué posición de caracter empieza y termina (1 = primer caracter
                      de la línea). Verá el texto que se extraería de esa línea en tiempo real.
                    </p>
                  </div>

                  <mat-form-field appearance="outline" class="full-field">
                    <mat-label>Línea de ejemplo (pegar del extracto)</mat-label>
                    <textarea matInput rows="2" class="linea-muestra"
                              [(ngModel)]="lineaMuestra" [ngModelOptions]="{standalone: true}"
                              (ngModelChange)="onLineaMuestraChange()"
                              placeholder="Ej: 01   06  Abono ACH BANCOLOMBIA...        800,670.00+"></textarea>
                  </mat-form-field>

                  <div formArrayName="anchoFijoColumnas" class="columnas-tabla">
                    <div class="columna-row" *ngFor="let col of anchoFijoColumnas.controls; let i = index" [formGroupName]="i">
                      <mat-form-field appearance="outline" class="col-campo">
                        <mat-label>Campo</mat-label>
                        <mat-select formControlName="campo">
                          <mat-option *ngFor="let c of camposAnchoFijo" [value]="c.valor">{{ c.etiqueta }}</mat-option>
                        </mat-select>
                      </mat-form-field>
                      <mat-form-field appearance="outline" class="col-pos">
                        <mat-label>Inicio</mat-label>
                        <input matInput type="number" formControlName="inicio" min="1">
                      </mat-form-field>
                      <mat-form-field appearance="outline" class="col-pos">
                        <mat-label>Fin</mat-label>
                        <input matInput type="number" formControlName="fin" min="1">
                      </mat-form-field>
                      <span class="col-preview" [matTooltip]="'Texto extraído de la línea de ejemplo'">
                        "{{ previewColumna(col) }}"
                      </span>
                      <button mat-icon-button type="button" class="delete-btn" (click)="quitarColumna(i)">
                        <mat-icon>delete_outline</mat-icon>
                      </button>
                    </div>
                  </div>
                  <button mat-button type="button" (click)="agregarColumna()" class="add-col-btn">
                    <mat-icon>add</mat-icon> Agregar columna
                  </button>

                  <div class="form-grid" style="margin-top: 16px;">
                    <mat-form-field appearance="outline">
                      <mat-label>Convención de signo</mat-label>
                      <mat-select formControlName="convencionSigno">
                        <mat-option value="SUFIJO">Sufijo (ej. 2,364,110.00-)</mat-option>
                        <mat-option value="PREFIJO">Prefijo (ej. -2,364,110.00)</mat-option>
                        <mat-option value="COLUMNAS_SEPARADAS">Columnas débito/crédito separadas</mat-option>
                        <mat-option value="COLUMNA_TIPO">Columna literal (DEBITO/CREDITO o D/C)</mat-option>
                      </mat-select>
                    </mat-form-field>

                    <mat-form-field appearance="outline" *ngIf="tieneColumna('fecha')">
                      <mat-label>Formato fecha (columna "Fecha completa")</mat-label>
                      <input matInput formControlName="anchoFijoFormatoFecha" placeholder="dd/MM/yyyy">
                      <mat-hint *ngIf="feedbackFormatoFecha() as fb" [class.hint-ok]="fb.ok" [class.hint-fail]="!fb.ok">
                        {{ fb.ok ? '✓' : '✗' }} {{ fb.mensaje }}
                      </mat-hint>
                    </mat-form-field>
                  </div>

                  <div class="checkbox-row" *ngIf="detalleForm.get('convencionSigno')?.value !== 'COLUMNAS_SEPARADAS'">
                    <mat-checkbox formControlName="anchoFijoInvertir">
                      Invertir signo — usar para tarjetas de crédito donde "+" es cargo y "-" es pago
                    </mat-checkbox>
                  </div>

                  <div class="checkbox-row">
                    <mat-checkbox formControlName="continuacionHabilitada">
                      El extracto tiene transacciones que continúan en una segunda línea física
                    </mat-checkbox>
                  </div>
                  <div class="form-grid" *ngIf="detalleForm.get('continuacionHabilitada')?.value">
                    <mat-form-field appearance="outline">
                      <mat-label>Campo ancla (vacío = posible continuación)</mat-label>
                      <mat-select formControlName="continuacionCampoAncla">
                        <mat-option *ngFor="let c of camposAnchoFijo" [value]="c.valor">{{ c.etiqueta }}</mat-option>
                      </mat-select>
                    </mat-form-field>
                    <mat-form-field appearance="outline">
                      <mat-label>Campo destino (a qué se concatena)</mat-label>
                      <mat-select formControlName="continuacionCampoDestino">
                        <mat-option *ngFor="let c of camposAnchoFijo" [value]="c.valor">{{ c.etiqueta }}</mat-option>
                      </mat-select>
                    </mat-form-field>
                  </div>
                </ng-container>

                <!-- ── Modo DELIMITADO / EXCEL: columnas por índice (como antes) ─── -->
                <ng-container *ngIf="tipoArchivo !== 'PDF' && !esTxtAnchoFijo()">
                  <div class="form-grid">
                    <mat-form-field appearance="outline" *ngIf="!esExcel()">
                      <mat-label>Separador</mat-label>
                      <input matInput formControlName="separador" placeholder=",">
                      <mat-hint>Para CSV/TXT: ',' ';' '|' '\\t'</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Filas a saltar</mat-label>
                      <input matInput type="number" formControlName="filasASaltar" min="0">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Encoding</mat-label>
                      <mat-select formControlName="encoding">
                        <mat-option value="UTF-8">UTF-8</mat-option>
                        <mat-option value="ISO-8859-1">ISO-8859-1 (Latin-1)</mat-option>
                        <mat-option value="windows-1252">Windows-1252</mat-option>
                      </mat-select>
                    </mat-form-field>

                    <mat-form-field appearance="outline" *ngIf="esExcel()">
                      <mat-label>Número de hoja</mat-label>
                      <input matInput type="number" formControlName="numeroHoja" min="0">
                      <mat-hint>Índice 0 = primera hoja</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna fecha</mat-label>
                      <input matInput type="number" formControlName="columnaFecha" min="0">
                      <mat-hint>Índice 0 = primera columna</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Formato fecha</mat-label>
                      <input matInput formControlName="formatoFecha" placeholder="dd/MM/yyyy">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna descripción</mat-label>
                      <input matInput type="number" formControlName="columnaDescripcion" min="0">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna referencia</mat-label>
                      <input matInput type="number" formControlName="columnaReferencia" min="0">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Factor de escala del monto</mat-label>
                      <input matInput type="number" formControlName="factorMonto" min="1">
                      <mat-hint>1 = pesos, 1000 = miles (ej. Bancolombia)</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Separador de miles</mat-label>
                      <input matInput formControlName="separadorMiles" placeholder=".">
                      <mat-hint>Ej: '.' (Colombia) o ',' (Bancolombia/EE.UU.)</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Separador decimal</mat-label>
                      <input matInput formControlName="separadorDecimales" placeholder=",">
                      <mat-hint>Ej: ',' (Colombia) o '.' (Bancolombia/EE.UU.)</mat-hint>
                    </mat-form-field>

                    <mat-form-field appearance="outline" *ngIf="tipoArchivo === 'CSV'">
                      <mat-label>Columna tipo_movimiento (opcional)</mat-label>
                      <input matInput type="number" formControlName="columnaTipoMovimiento" min="0">
                      <mat-hint>Si el monto no trae signo y hay una columna literal DEBITO/CREDITO</mat-hint>
                    </mat-form-field>
                  </div>

                  <div class="checkbox-row">
                    <mat-checkbox formControlName="debitoYCreditoSeparados"
                                  (change)="onDebitosCreditosSeparadosChange()">
                      Débito y crédito en columnas separadas
                    </mat-checkbox>
                  </div>

                  <div class="form-grid" *ngIf="!detalleForm.get('debitoYCreditoSeparados')?.value">
                    <mat-form-field appearance="outline">
                      <mat-label>Columna monto</mat-label>
                      <input matInput type="number" formControlName="columnaMonto" min="0">
                    </mat-form-field>
                  </div>

                  <div class="form-grid" *ngIf="detalleForm.get('debitoYCreditoSeparados')?.value">
                    <mat-form-field appearance="outline">
                      <mat-label>Columna débito</mat-label>
                      <input matInput type="number" formControlName="columnaDebito" min="0">
                    </mat-form-field>

                    <mat-form-field appearance="outline">
                      <mat-label>Columna crédito</mat-label>
                      <input matInput type="number" formControlName="columnaCredito" min="0">
                    </mat-form-field>
                  </div>
                </ng-container>

                <!-- ── Validar cuadre (todos los modos) ─────────────────────────── -->
                <ng-container *ngIf="tipoArchivo !== 'PDF'">
                  <div class="checkbox-row">
                    <mat-checkbox formControlName="cuadreHabilitada">
                      Validar cuadre (saldo anterior + créditos − débitos = saldo final)
                    </mat-checkbox>
                  </div>
                  <div class="form-grid" *ngIf="detalleForm.get('cuadreHabilitada')?.value">
                    <mat-form-field appearance="outline">
                      <mat-label>Etiqueta "Saldo anterior"</mat-label>
                      <input matInput formControlName="cuadreEtiquetaSaldoAnterior" placeholder="Saldo Anterior">
                    </mat-form-field>
                    <mat-form-field appearance="outline">
                      <mat-label>Etiqueta "Créditos"</mat-label>
                      <input matInput formControlName="cuadreEtiquetaCreditos" placeholder="Más Créditos">
                    </mat-form-field>
                    <mat-form-field appearance="outline">
                      <mat-label>Etiqueta "Débitos"</mat-label>
                      <input matInput formControlName="cuadreEtiquetaDebitos" placeholder="Menos Débitos">
                    </mat-form-field>
                    <mat-form-field appearance="outline">
                      <mat-label>Etiqueta "Saldo final"</mat-label>
                      <input matInput formControlName="cuadreEtiquetaSaldoFinal" placeholder="Nuevo Saldo">
                    </mat-form-field>
                  </div>
                  <mat-hint class="cuadre-hint" *ngIf="detalleForm.get('cuadreHabilitada')?.value">
                    Busque el texto tal cual aparece en el extracto (sin tildes si el archivo no las trae).
                    No se necesita regex: se toma el primer número que aparece después de la etiqueta.
                  </mat-hint>
                </ng-container>

                <!-- ── Probar con archivo de muestra ────────────────────────────── -->
                <div class="prueba-card" *ngIf="tipoArchivo !== 'PDF'">
                  <div class="info-header">
                    <mat-icon>science</mat-icon>
                    <strong>Probar con archivo de muestra</strong>
                  </div>
                  <div class="prueba-actions">
                    <mat-form-field appearance="outline" class="periodo-field">
                      <mat-label>Periodo (opcional, ej: 2026-06)</mat-label>
                      <input matInput [(ngModel)]="periodoPrueba" [ngModelOptions]="{standalone: true}" placeholder="2026-06">
                    </mat-form-field>
                    <input type="file" #archivoMuestra hidden (change)="onArchivoMuestraSeleccionado($event)">
                    <button mat-stroked-button type="button" (click)="archivoMuestra.click()" [disabled]="probando">
                      <mat-spinner diameter="16" *ngIf="probando"></mat-spinner>
                      <mat-icon *ngIf="!probando">upload_file</mat-icon>
                      Seleccionar archivo y probar
                    </button>
                  </div>

                  <div class="error-message" *ngIf="errorPrueba">
                    <mat-icon>error_outline</mat-icon>{{ errorPrueba }}
                  </div>

                  <div class="resultado-prueba" *ngIf="resultadoPrueba">
                    <div class="cuadre-card" [ngClass]="cuadreClass()">
                      <mat-icon>{{ resultadoPrueba.cuadre.cuadra ? 'check_circle' : 'warning' }}</mat-icon>
                      <div *ngIf="resultadoPrueba.cuadre.habilitada">
                        <strong>{{ resultadoPrueba.cuadre.cuadra ? 'El extracto cuadra' : 'El extracto NO cuadra' }}</strong>
                        <div class="cuadre-cifras" *ngIf="resultadoPrueba.cuadre.saldoAnterior !== undefined">
                          Saldo anterior: {{ resultadoPrueba.cuadre.saldoAnterior }} +
                          Créditos: {{ resultadoPrueba.cuadre.creditos }} −
                          Débitos: {{ resultadoPrueba.cuadre.debitos }} =
                          Calculado: {{ resultadoPrueba.cuadre.saldoCalculado }}
                          (saldo final del archivo: {{ resultadoPrueba.cuadre.saldoFinal }})
                        </div>
                      </div>
                      <div *ngIf="!resultadoPrueba.cuadre.habilitada">Validación de cuadre no habilitada.</div>
                    </div>

                    <div class="advertencias" *ngIf="resultadoPrueba.advertencias.length">
                      <div *ngFor="let a of resultadoPrueba.advertencias" class="advertencia-item">
                        <mat-icon>info_outline</mat-icon>{{ a }}
                      </div>
                    </div>

                    <p class="preview-total">{{ resultadoPrueba.totalMovimientos }} movimiento(s) encontrado(s)
                      <span *ngIf="resultadoPrueba.movimientos.length < resultadoPrueba.totalMovimientos">
                        (mostrando los primeros {{ resultadoPrueba.movimientos.length }})</span>
                    </p>

                    <table mat-table [dataSource]="resultadoPrueba.movimientos" class="data-table"
                           *ngIf="resultadoPrueba.movimientos.length">
                      <ng-container matColumnDef="fecha">
                        <th mat-header-cell *matHeaderCellDef>Fecha</th>
                        <td mat-cell *matCellDef="let m">{{ m.fecha }}</td>
                      </ng-container>
                      <ng-container matColumnDef="descripcion">
                        <th mat-header-cell *matHeaderCellDef>Descripción</th>
                        <td mat-cell *matCellDef="let m">{{ m.descripcion }}</td>
                      </ng-container>
                      <ng-container matColumnDef="monto">
                        <th mat-header-cell *matHeaderCellDef>Monto</th>
                        <td mat-cell *matCellDef="let m">{{ m.monto }}</td>
                      </ng-container>
                      <ng-container matColumnDef="tipo">
                        <th mat-header-cell *matHeaderCellDef>Tipo</th>
                        <td mat-cell *matCellDef="let m">{{ m.tipo }}</td>
                      </ng-container>
                      <tr mat-header-row *matHeaderRowDef="previewColumns"></tr>
                      <tr mat-row *matRowDef="let row; columns: previewColumns;"></tr>
                    </table>
                  </div>
                </div>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn"
                          *ngIf="!editandoId">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="next-btn" matStepperNext>
                    Siguiente
                    <mat-icon>arrow_forward</mat-icon>
                  </button>
                </div>
              </form>
            </mat-step>

            <!-- PASO 5: Cuentas asociadas -->
            <mat-step label="Cuentas">
              <form [formGroup]="cuentasForm" class="step-form">
                <div class="checkbox-row">
                  <mat-checkbox formControlName="aplicaParaTodasLasCuentas"
                                (change)="onAplicaTodosChange()">
                    Aplica para todas las cuentas del banco
                  </mat-checkbox>
                </div>

                <mat-form-field appearance="outline" class="full-field"
                                *ngIf="!cuentasForm.get('aplicaParaTodasLasCuentas')?.value">
                  <mat-label>Cuentas específicas</mat-label>
                  <mat-select formControlName="idsCuentas" multiple>
                    <mat-option *ngFor="let c of cuentas" [value]="c.id">
                      {{ c.numeroCuenta }}{{ c.descripcion ? ' — ' + c.descripcion : '' }}
                    </mat-option>
                  </mat-select>
                  <mat-hint>Seleccione una o más cuentas</mat-hint>
                </mat-form-field>

                <div class="error-message" *ngIf="errorGuardar">
                  <mat-icon>error_outline</mat-icon>{{ errorGuardar }}
                </div>

                <div class="step-actions">
                  <button mat-button matStepperPrevious class="back-step-btn"
                          *ngIf="!editandoId">
                    <mat-icon>arrow_back</mat-icon>
                    Anterior
                  </button>
                  <button mat-flat-button class="save-btn" (click)="guardar()" [disabled]="saving">
                    <mat-spinner diameter="18" *ngIf="saving"></mat-spinner>
                    <mat-icon *ngIf="!saving">save</mat-icon>
                    <span>{{ saving ? 'Guardando...' : (editandoId ? 'Actualizar' : 'Guardar') }}</span>
                  </button>
                </div>
              </form>
            </mat-step>

          </mat-stepper>
        </mat-card-content>
      </mat-card>

      <!-- Lista de configuraciones existentes -->
      <mat-card class="table-card" *ngIf="idBancoSeleccionado">
        <mat-card-header>
          <mat-card-title>Configuraciones para {{ nombreBancoSeleccionado }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loadingLista" class="loading-container">
            <mat-spinner diameter="36"></mat-spinner>
          </div>

          <div *ngIf="!loadingLista && configuraciones.length === 0" class="empty-state">
            <mat-icon>settings</mat-icon>
            <p>No hay configuraciones para este banco</p>
          </div>

          <table mat-table [dataSource]="configuraciones"
                 *ngIf="!loadingLista && configuraciones.length > 0" class="data-table">

            <ng-container matColumnDef="nombre">
              <th mat-header-cell *matHeaderCellDef>Nombre</th>
              <td mat-cell *matCellDef="let row">
                <span class="config-nombre">{{ row.nombre }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="tipoArchivo">
              <th mat-header-cell *matHeaderCellDef>Tipo</th>
              <td mat-cell *matCellDef="let row">
                <span class="tipo-chip tipo-{{ row.tipoArchivo.toLowerCase() }}">
                  {{ row.tipoArchivo }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="cuentas">
              <th mat-header-cell *matHeaderCellDef>Cuentas</th>
              <td mat-cell *matCellDef="let row">
                <span *ngIf="row.aplicaParaTodasLasCuentas" class="todas-chip">Todas</span>
                <span *ngIf="!row.aplicaParaTodasLasCuentas">
                  {{ row.idsCuentas?.length ?? 0 }} cuenta(s)
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="activo">
              <th mat-header-cell *matHeaderCellDef>Estado</th>
              <td mat-cell *matCellDef="let row">
                <span class="activo-chip" [ngClass]="row.activo ? 'activo' : 'inactivo'">
                  {{ row.activo ? 'Activa' : 'Inactiva' }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="acciones">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row">
                <button mat-icon-button class="edit-btn" (click)="editar(row)"
                        matTooltip="Editar" *ngIf="canManage()">
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button class="delete-btn" (click)="eliminar(row)"
                        matTooltip="Eliminar" *ngIf="canManage()">
                  <mat-icon>delete_outline</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns;" class="data-row"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .page-container { padding: 32px; max-width: 960px; }

    .page-header { margin-bottom: 24px; }

    .back-btn { color: #6b7a8d; gap: 4px; margin-bottom: 8px; padding: 0; }

    .page-title { font-size: 26px; font-weight: 600; color: #1a2332; margin: 0 0 4px; }
    .page-subtitle { font-size: 14px; color: #6b7a8d; margin: 0; }

    .stepper-card, .table-card {
      border-radius: 10px !important;
      box-shadow: 0 2px 8px rgba(0,0,0,0.06) !important;
      margin-bottom: 24px;
    }

    mat-card-title { font-size: 15px !important; font-weight: 600 !important; color: #1a2332 !important; }

    .stepper { background: transparent; }

    .step-form { padding: 24px 0 8px; }

    .full-field { width: 100%; max-width: 480px; }

    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 12px;
    }

    .checkbox-row {
      margin: 12px 0;
    }

    .pdf-notice {
      display: flex;
      align-items: center;
      gap: 10px;
      background: #e8f4fd;
      border: 1px solid #90caf9;
      border-radius: 8px;
      padding: 14px 18px;
      color: #1565c0;
      font-size: 14px;
      margin-bottom: 16px;
    }

    .step-actions {
      display: flex;
      gap: 12px;
      margin-top: 24px;
      align-items: center;
    }

    .next-btn {
      background: #1a2332 !important;
      color: #fff !important;
      border-radius: 8px !important;
      gap: 6px;
    }

    .back-step-btn { color: #6b7a8d; gap: 4px; }

    .save-btn {
      background: #3d7ebf !important;
      color: #fff !important;
      border-radius: 8px !important;
      gap: 6px;
      display: flex;
      align-items: center;
    }

    .error-message {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #e53935;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 8px;
      padding: 10px 14px;
      font-size: 13px;
      margin-top: 8px;
      margin-bottom: 8px;
    }

    .loading-container { display: flex; justify-content: center; padding: 60px; }

    .empty-state {
      display: flex; flex-direction: column; align-items: center;
      padding: 40px; color: #6b7a8d; gap: 12px;
    }

    .empty-state mat-icon { font-size: 48px; width: 48px; height: 48px; color: #b0bec5; }

    .data-table { width: 100%; }

    .mat-mdc-header-row { background: #f8fafc; }

    .mat-mdc-header-cell {
      font-size: 12px !important; font-weight: 600 !important;
      color: #6b7a8d !important; text-transform: uppercase; letter-spacing: 0.5px;
    }

    .mat-mdc-cell { font-size: 14px; color: #1a2332; padding: 12px 16px !important; }

    .data-row:hover { background: #f8fafc; }

    .config-nombre { font-weight: 500; }

    .tipo-chip {
      padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500;
    }
    .tipo-csv   { background: #dbeafe; color: #1e40af; }
    .tipo-txt   { background: #f3f4f6; color: #374151; }
    .tipo-xls   { background: #dcfce7; color: #166534; }
    .tipo-xlsx  { background: #d1fae5; color: #065f46; }
    .tipo-pdf   { background: #fee2e2; color: #991b1b; }

    .todas-chip {
      background: #fef3c7; color: #92400e;
      padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500;
    }

    .activo-chip { padding: 3px 10px; border-radius: 20px; font-size: 12px; font-weight: 500; }
    .activo   { background: #dcfce7; color: #166534; }
    .inactivo { background: #fee2e2; color: #991b1b; }

    .edit-btn { color: #3d7ebf; }
    .delete-btn { color: #e53935; }

    .ancho-fijo-info {
      background: #f0f4ff;
      border: 1px solid #c7d7f5;
      border-radius: 8px;
      padding: 14px 18px;
      margin-bottom: 18px;
      font-size: 13px;
      color: #1a2332;
    }
    .info-header {
      display: flex; align-items: center; gap: 8px;
      color: #1e40af; font-size: 14px; margin-bottom: 8px;
    }
    .info-desc { margin: 0; color: #374151; line-height: 1.5; }

    .linea-muestra { font-family: monospace; font-size: 13px; white-space: pre; }

    .columnas-tabla { display: flex; flex-direction: column; gap: 4px; margin-bottom: 8px; }
    .columna-row {
      display: flex; align-items: center; gap: 10px;
    }
    .col-campo { width: 180px; }
    .col-pos { width: 90px; }
    .col-preview {
      font-family: monospace; font-size: 13px; background: #f1f5f9;
      padding: 4px 8px; border-radius: 4px; flex: 1; color: #1a2332;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .add-col-btn { color: #3d7ebf; margin-bottom: 8px; }

    .prueba-card {
      background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
      padding: 16px 18px; margin-top: 20px;
    }
    .prueba-actions { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
    .periodo-field { width: 220px; }

    .resultado-prueba { margin-top: 16px; }
    .cuadre-card {
      display: flex; align-items: flex-start; gap: 10px;
      padding: 12px 16px; border-radius: 8px; font-size: 13px; margin-bottom: 12px;
    }
    .cuadre-card.ok { background: #dcfce7; color: #166534; }
    .cuadre-card.fail { background: #fef2f2; color: #991b1b; }
    .cuadre-cifras { margin-top: 4px; font-size: 12px; }

    .advertencias { margin-bottom: 12px; }
    .advertencia-item {
      display: flex; align-items: center; gap: 6px; font-size: 12px;
      color: #92400e; background: #fef3c7; padding: 6px 10px; border-radius: 6px; margin-bottom: 4px;
    }
    .preview-total { font-size: 13px; color: #6b7a8d; margin: 8px 0; }
    .cuadre-hint { display: block; font-size: 12px; color: #6b7a8d; margin-top: 4px; }
    .hint-ok { color: #166534 !important; }
    .hint-fail { color: #e53935 !important; }
  `]
})
export class ConfiguracionExtractoComponent implements OnInit {

  @ViewChild('stepper') stepper!: MatStepper;

  bancos: Banco[] = [];
  cuentas: Cuenta[] = [];
  configuraciones: ConfiguracionExtracto[] = [];

  bancoForm: FormGroup;
  nombreForm: FormGroup;
  tipoForm: FormGroup;
  detalleForm: FormGroup;
  cuentasForm: FormGroup;

  idBancoSeleccionado: number | null = null;
  nombreBancoSeleccionado = '';
  tipoArchivo: TipoArchivoExtracto | null = null;

  editandoId: number | null = null;
  saving = false;
  loadingLista = false;
  errorGuardar = '';

  camposAnchoFijo = CAMPOS_ANCHO_FIJO;
  lineaMuestra = '';
  periodoPrueba = '';
  probando = false;
  errorPrueba = '';
  resultadoPrueba: PruebaConfiguracionResultado | null = null;
  previewColumns = ['fecha', 'descripcion', 'monto', 'tipo'];

  columns = ['nombre', 'tipoArchivo', 'cuentas', 'activo', 'acciones'];

  constructor(
    private api: ApiService,
    public auth: AuthService,
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router
  ) {
    this.bancoForm = this.fb.group({
      idBanco: [null, Validators.required]
    });
    this.nombreForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(200)]]
    });
    this.tipoForm = this.fb.group({
      tipoArchivo: [null, Validators.required]
    });
    this.detalleForm = this.fb.group({
      tipoOrigen: ['DELIMITADO' as TipoOrigenExtracto],

      // Comunes (delimitado/excel)
      separador: [','],
      filasASaltar: [0],
      columnaFecha: [0],
      formatoFecha: ['dd/MM/yyyy'],
      columnaDescripcion: [1],
      columnaReferencia: [-1],
      columnaMonto: [2],
      columnaDebito: [-1],
      columnaCredito: [-1],
      columnaTipoMovimiento: [-1],
      debitoYCreditoSeparados: [false],
      encoding: ['UTF-8'],
      numeroHoja: [0],
      factorMonto: [1],
      separadorMiles: ['.'],
      separadorDecimales: [','],

      // Ancho fijo
      anchoFijoColumnas: this.fb.array([]),
      convencionSigno: ['SUFIJO' as ConvencionSigno],
      anchoFijoFormatoFecha: ['dd/MM/yyyy'],
      anchoFijoInvertir: [false],

      // Continuación de línea (todos los modos, principalmente ancho fijo)
      continuacionHabilitada: [false],
      continuacionCampoAncla: ['dia'],
      continuacionCampoDestino: ['descripcion'],

      // Cuadre (todos los modos)
      cuadreHabilitada: [false],
      cuadreEtiquetaSaldoAnterior: [''],
      cuadreEtiquetaCreditos: [''],
      cuadreEtiquetaDebitos: [''],
      cuadreEtiquetaSaldoFinal: ['']
    });
    this.cuentasForm = this.fb.group({
      aplicaParaTodasLasCuentas: [true],
      idsCuentas: [[]]
    });
  }

  get anchoFijoColumnas(): FormArray {
    return this.detalleForm.get('anchoFijoColumnas') as FormArray;
  }

  ngOnInit(): void {
    this.api.listarBancos().subscribe({
      next: res => {
        this.bancos = res.data;
        const idBancoParam = this.route.snapshot.paramMap.get('idBanco');
        if (idBancoParam) {
          const id = Number(idBancoParam);
          this.bancoForm.patchValue({ idBanco: id });
          this.onBancoChange(id);
        }
      }
    });

    // Cada vez que cambian las posiciones de columnas, re-intenta detectar el
    // formato de fecha automáticamente a partir de la línea de ejemplo pegada.
    this.anchoFijoColumnas.valueChanges.subscribe(() => this.autoDetectarFormatoFecha());
  }

  onLineaMuestraChange(): void {
    this.autoDetectarFormatoFecha();
  }

  /**
   * Infiere el patrón de fecha (ej. "yyyyMMdd", "dd/MM/yyyy") a partir del texto
   * real extraído por la columna "Fecha completa" en la línea de ejemplo pegada,
   * en vez de dejar que el usuario adivine a partir del placeholder del campo
   * (que fue la causa de un error real: alguien vio "dd/MM/yyyy" como placeholder
   * y asumió que el archivo traía separadores cuando en realidad no los trae).
   */
  private autoDetectarFormatoFecha(): void {
    const col = this.anchoFijoColumnas.controls.find(c => c.get('campo')?.value === 'fecha');
    if (!col || !this.lineaMuestra) return;
    const texto = this.previewColumna(col);
    const formato = this.detectarFormatoFecha(texto);
    if (formato) {
      this.detalleForm.get('anchoFijoFormatoFecha')?.setValue(formato, { emitEvent: false });
    }
  }

  detectarFormatoFecha(texto: string): string | null {
    const t = (texto || '').trim();
    if (!t) return null;

    const sepMatch = t.match(/[^0-9]/);
    if (!sepMatch) {
      // Todo dígitos, sin separador (ej. "20260428" o "28042026")
      if (t.length !== 8) return null;
      const primeros4 = Number(t.substring(0, 4));
      const esAnioPlausible = primeros4 >= 1900 && primeros4 <= 2100;
      return esAnioPlausible ? 'yyyyMMdd' : 'ddMMyyyy';
    }

    const sep = sepMatch[0];
    const partes = t.split(sep);
    if (partes.length !== 3) return null;
    if (partes[0].length === 4) return `yyyy${sep}MM${sep}dd`;
    if (partes[2].length === 4) return `dd${sep}MM${sep}yyyy`;
    return null;
  }

  /** Retroalimentación en vivo: ¿el formato actual sí interpreta la línea de ejemplo? */
  feedbackFormatoFecha(): { ok: boolean; mensaje: string } | null {
    const col = this.anchoFijoColumnas.controls.find(c => c.get('campo')?.value === 'fecha');
    if (!col || !this.lineaMuestra) return null;
    const texto = this.previewColumna(col);
    if (!texto) return null;
    const formato = this.detalleForm.get('anchoFijoFormatoFecha')?.value;
    if (!formato) return null;

    const fecha = this.intentarParsearFecha(texto, formato);
    return fecha
      ? { ok: true, mensaje: `se interpreta como ${fecha.toLocaleDateString('es-CO')}` }
      : { ok: false, mensaje: `"${texto}" no coincide con el formato "${formato}"` };
  }

  /** Parser mínimo de patrones tipo Java (yyyy/MM/dd, dd-MM-yyyy, yyyyMMdd, etc.) solo para dar feedback visual. */
  private intentarParsearFecha(texto: string, formato: string): Date | null {
    let regex = '';
    const grupos: { tipo: 'y' | 'M' | 'd'; len: number }[] = [];
    let i = 0;
    while (i < formato.length) {
      const c = formato[i];
      if (c === 'y' || c === 'M' || c === 'd') {
        let j = i;
        while (j < formato.length && formato[j] === c) j++;
        grupos.push({ tipo: c, len: j - i });
        regex += `(\\d{${j - i}})`;
        i = j;
      } else {
        regex += c.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        i++;
      }
    }

    const m = new RegExp('^' + regex + '$').exec(texto.trim());
    if (!m) return null;

    let anio = 0, mes = 1, dia = 1;
    grupos.forEach((g, idx) => {
      const v = Number(m[idx + 1]);
      if (g.tipo === 'y') anio = g.len <= 2 ? 2000 + v : v;
      if (g.tipo === 'M') mes = v;
      if (g.tipo === 'd') dia = v;
    });

    const fecha = new Date(anio, mes - 1, dia);
    const esValida = fecha.getFullYear() === anio && fecha.getMonth() === mes - 1 && fecha.getDate() === dia;
    return esValida ? fecha : null;
  }

  onBancoChange(idBanco: number): void {
    this.idBancoSeleccionado = idBanco;
    const banco = this.bancos.find(b => b.id === idBanco);
    this.nombreBancoSeleccionado = banco?.nombre ?? '';
    this.cargarCuentas(idBanco);
    this.cargarConfiguraciones(idBanco);
  }

  onTipoArchivoChange(): void {
    this.tipoArchivo = this.tipoForm.get('tipoArchivo')?.value as TipoArchivoExtracto;
    if (this.tipoArchivo === 'CSV') {
      this.detalleForm.patchValue({ tipoOrigen: 'DELIMITADO' });
    } else if (this.tipoArchivo === 'XLS' || this.tipoArchivo === 'XLSX') {
      this.detalleForm.patchValue({ tipoOrigen: 'EXCEL' });
    } else if (this.tipoArchivo === 'TXT') {
      this.detalleForm.patchValue({ tipoOrigen: 'DELIMITADO' });
    }
    this.limpiarResultadoPrueba();
  }

  onTipoOrigenChange(): void {
    if (this.esTxtAnchoFijo() && this.anchoFijoColumnas.length === 0) {
      // Columnas por defecto típicas de un reporte de impresión (ej. Davivienda)
      this.agregarColumna('dia', 1, 2);
      this.agregarColumna('mes', 4, 5);
      this.agregarColumna('descripcion', 8, 60);
      this.agregarColumna('monto', 61, 85);
      this.detalleForm.patchValue({ encoding: 'ISO-8859-1', separadorMiles: ',', separadorDecimales: '.' });
    }
    this.limpiarResultadoPrueba();
  }

  onDebitosCreditosSeparadosChange(): void {
    // campos se muestran/ocultan via *ngIf
  }

  onAplicaTodosChange(): void {
    // cuentas field se muestra/oculta via *ngIf
  }

  esExcel(): boolean {
    return this.tipoArchivo === 'XLS' || this.tipoArchivo === 'XLSX';
  }

  esTxtAnchoFijo(): boolean {
    return this.tipoArchivo === 'TXT' && this.detalleForm.get('tipoOrigen')?.value === 'ANCHO_FIJO';
  }

  tieneColumna(campo: CampoAnchoFijo): boolean {
    return this.anchoFijoColumnas.controls.some(c => c.get('campo')?.value === campo);
  }

  agregarColumna(campo: CampoAnchoFijo = 'descripcion', inicio = 1, fin = 1): void {
    this.anchoFijoColumnas.push(this.fb.group({
      campo: [campo],
      inicio: [inicio],
      fin: [fin]
    }));
  }

  quitarColumna(index: number): void {
    this.anchoFijoColumnas.removeAt(index);
  }

  previewColumna(col: any): string {
    const inicio = Number(col.get('inicio')?.value) || 1;
    const fin = Number(col.get('fin')?.value) || 1;
    if (!this.lineaMuestra) return '';
    const desde = Math.max(0, inicio - 1);
    const hasta = Math.min(this.lineaMuestra.length, fin);
    if (desde >= hasta) return '';
    return this.lineaMuestra.substring(desde, hasta).trim();
  }

  cargarCuentas(idBanco: number): void {
    this.api.listarCuentasPorBanco(idBanco).subscribe({
      next: res => { this.cuentas = res.data; }
    });
  }

  cargarConfiguraciones(idBanco: number): void {
    this.loadingLista = true;
    this.api.listarConfiguracionesPorBanco(idBanco).subscribe({
      next: res => { this.configuraciones = res.data; this.loadingLista = false; },
      error: () => { this.loadingLista = false; }
    });
  }

  /** Arma el schema tipado ConfiguracionExtractoDetalle a partir del formulario. */
  private construirDetalle(): ConfiguracionExtractoDetalle {
    const v = this.detalleForm.value;
    const detalle: ConfiguracionExtractoDetalle = {
      tipoOrigen: v.tipoOrigen,
      encoding: v.encoding,
      factorMonto: v.factorMonto,
      separadorMiles: v.separadorMiles,
      separadorDecimales: v.separadorDecimales,
      continuacion: {
        habilitada: !!v.continuacionHabilitada,
        campoAncla: v.continuacionCampoAncla,
        campoDestino: v.continuacionCampoDestino
      },
      cuadre: {
        habilitada: !!v.cuadreHabilitada,
        etiquetaSaldoAnterior: v.cuadreEtiquetaSaldoAnterior,
        etiquetaCreditos: v.cuadreEtiquetaCreditos,
        etiquetaDebitos: v.cuadreEtiquetaDebitos,
        etiquetaSaldoFinal: v.cuadreEtiquetaSaldoFinal
      }
    };

    if (v.tipoOrigen === 'ANCHO_FIJO') {
      detalle.anchoFijo = {
        columnas: (v.anchoFijoColumnas || []).map((c: any) => ({ campo: c.campo, inicio: c.inicio, fin: c.fin })),
        convencionSigno: v.convencionSigno,
        formatoFecha: v.anchoFijoFormatoFecha,
        invertir: !!v.anchoFijoInvertir
      };
    } else if (v.tipoOrigen === 'EXCEL') {
      detalle.excel = {
        numeroHoja: v.numeroHoja,
        filasASaltar: v.filasASaltar,
        columnaFecha: v.columnaFecha,
        formatoFecha: v.formatoFecha,
        columnaDescripcion: v.columnaDescripcion,
        columnaReferencia: v.columnaReferencia,
        columnaMonto: v.columnaMonto,
        columnaDebito: v.columnaDebito,
        columnaCredito: v.columnaCredito,
        debitoYCreditoSeparados: v.debitoYCreditoSeparados
      };
    } else {
      detalle.delimitado = {
        separador: v.separador,
        filasASaltar: v.filasASaltar,
        columnaFecha: v.columnaFecha,
        formatoFecha: v.formatoFecha,
        columnaDescripcion: v.columnaDescripcion,
        columnaReferencia: v.columnaReferencia,
        columnaMonto: v.columnaMonto,
        columnaDebito: v.columnaDebito,
        columnaCredito: v.columnaCredito,
        columnaTipoMovimiento: v.columnaTipoMovimiento,
        debitoYCreditoSeparados: v.debitoYCreditoSeparados
      };
    }

    return detalle;
  }

  onArchivoMuestraSeleccionado(event: Event): void {
    const input = event.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo) return;

    this.probando = true;
    this.errorPrueba = '';
    this.resultadoPrueba = null;

    const detalle = this.construirDetalle();
    this.api.probarConfiguracion(archivo, detalle, this.periodoPrueba || undefined).subscribe({
      next: res => {
        this.resultadoPrueba = res.data;
        this.probando = false;
      },
      error: err => {
        this.errorPrueba = err.error?.message ?? 'Error al probar la configuración';
        this.probando = false;
      }
    });
    input.value = '';
  }

  cuadreClass(): string {
    if (!this.resultadoPrueba) return '';
    return this.resultadoPrueba.cuadre.habilitada && !this.resultadoPrueba.cuadre.cuadra ? 'fail' : 'ok';
  }

  private limpiarResultadoPrueba(): void {
    this.resultadoPrueba = null;
    this.errorPrueba = '';
  }

  guardar(): void {
    this.errorGuardar = '';
    this.saving = true;

    // Cuando se edita, banco/nombre/tipo no se modifican: leer del objeto original
    // para evitar que el stepper invalide esos formularios al no haber sido visitados.
    let idBanco: number | null;
    let nombre: string;
    let tipoArchivo: string;
    if (this.editandoId) {
      const original = this.configuraciones.find(c => c.id === this.editandoId);
      idBanco      = original?.idBanco      ?? this.bancoForm.get('idBanco')?.value;
      nombre       = original?.nombre       ?? this.nombreForm.get('nombre')?.value;
      tipoArchivo  = original?.tipoArchivo  ?? this.tipoForm.get('tipoArchivo')?.value;
    } else {
      idBanco     = this.bancoForm.get('idBanco')?.value;
      nombre      = this.nombreForm.get('nombre')?.value;
      tipoArchivo = this.tipoForm.get('tipoArchivo')?.value;
    }

    const aplicaParaTodasLasCuentas = this.cuentasForm.get('aplicaParaTodasLasCuentas')?.value;
    const idsCuentas = aplicaParaTodasLasCuentas ? [] : (this.cuentasForm.get('idsCuentas')?.value ?? []);

    const configuracionDetalle = tipoArchivo !== 'PDF' ? this.construirDetalle() : null;

    const payload = {
      idBanco,
      nombre,
      tipoArchivo,
      aplicaParaTodasLasCuentas,
      idsCuentas,
      configuracionDetalle
    };

    const obs = this.editandoId
      ? this.api.actualizarConfiguracion(this.editandoId, payload)
      : this.api.crearConfiguracion(payload);

    obs.subscribe({
      next: res => {
        if (this.editandoId) {
          this.configuraciones = this.configuraciones.map(c =>
            c.id === this.editandoId ? res.data : c
          );
        } else {
          this.configuraciones = [...this.configuraciones, res.data];
        }
        this.resetForms();
        this.saving = false;
      },
      error: err => {
        this.saving = false;
        this.errorGuardar = err.error?.message ?? 'Error al guardar la configuración';
      }
    });
  }

  editar(config: ConfiguracionExtracto): void {
    this.editandoId = config.id;
    this.bancoForm.patchValue({ idBanco: config.idBanco });
    this.nombreForm.patchValue({ nombre: config.nombre });
    this.tipoForm.patchValue({ tipoArchivo: config.tipoArchivo });
    this.tipoArchivo = config.tipoArchivo;
    this.cuentasForm.patchValue({
      aplicaParaTodasLasCuentas: config.aplicaParaTodasLasCuentas,
      idsCuentas: config.idsCuentas ?? []
    });

    if (config.configuracionDetalle) {
      try {
        const detalle: ConfiguracionExtractoDetalle = JSON.parse(config.configuracionDetalle);
        this.aplicarDetalleAlFormulario(detalle);
      } catch (_) { /* ignore */ }
    }

    setTimeout(() => {
      this.stepper.selectedIndex = 3;
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }, 0);
  }

  private aplicarDetalleAlFormulario(detalle: ConfiguracionExtractoDetalle): void {
    this.detalleForm.patchValue({
      tipoOrigen: detalle.tipoOrigen,
      encoding: detalle.encoding ?? 'UTF-8',
      factorMonto: detalle.factorMonto ?? 1,
      separadorMiles: detalle.separadorMiles ?? '.',
      separadorDecimales: detalle.separadorDecimales ?? ',',
      continuacionHabilitada: detalle.continuacion?.habilitada ?? false,
      continuacionCampoAncla: detalle.continuacion?.campoAncla ?? 'dia',
      continuacionCampoDestino: detalle.continuacion?.campoDestino ?? 'descripcion',
      cuadreHabilitada: detalle.cuadre?.habilitada ?? false,
      cuadreEtiquetaSaldoAnterior: detalle.cuadre?.etiquetaSaldoAnterior ?? '',
      cuadreEtiquetaCreditos: detalle.cuadre?.etiquetaCreditos ?? '',
      cuadreEtiquetaDebitos: detalle.cuadre?.etiquetaDebitos ?? '',
      cuadreEtiquetaSaldoFinal: detalle.cuadre?.etiquetaSaldoFinal ?? ''
    });

    if (detalle.tipoOrigen === 'ANCHO_FIJO' && detalle.anchoFijo) {
      this.anchoFijoColumnas.clear();
      for (const col of detalle.anchoFijo.columnas || []) {
        this.agregarColumna(col.campo, col.inicio, col.fin);
      }
      this.detalleForm.patchValue({
        convencionSigno: detalle.anchoFijo.convencionSigno,
        anchoFijoFormatoFecha: detalle.anchoFijo.formatoFecha ?? 'dd/MM/yyyy',
        anchoFijoInvertir: detalle.anchoFijo.invertir ?? false
      });
    } else if (detalle.tipoOrigen === 'EXCEL' && detalle.excel) {
      this.detalleForm.patchValue({
        numeroHoja: detalle.excel.numeroHoja ?? 0,
        filasASaltar: detalle.excel.filasASaltar ?? 0,
        columnaFecha: detalle.excel.columnaFecha ?? 0,
        formatoFecha: detalle.excel.formatoFecha ?? 'dd/MM/yyyy',
        columnaDescripcion: detalle.excel.columnaDescripcion ?? 1,
        columnaReferencia: detalle.excel.columnaReferencia ?? -1,
        columnaMonto: detalle.excel.columnaMonto ?? 4,
        columnaDebito: detalle.excel.columnaDebito ?? -1,
        columnaCredito: detalle.excel.columnaCredito ?? -1,
        debitoYCreditoSeparados: detalle.excel.debitoYCreditoSeparados ?? false
      });
    } else if (detalle.delimitado) {
      this.detalleForm.patchValue({
        separador: detalle.delimitado.separador ?? ',',
        filasASaltar: detalle.delimitado.filasASaltar ?? 0,
        columnaFecha: detalle.delimitado.columnaFecha ?? 0,
        formatoFecha: detalle.delimitado.formatoFecha ?? 'dd/MM/yyyy',
        columnaDescripcion: detalle.delimitado.columnaDescripcion ?? 1,
        columnaReferencia: detalle.delimitado.columnaReferencia ?? -1,
        columnaMonto: detalle.delimitado.columnaMonto ?? 2,
        columnaDebito: detalle.delimitado.columnaDebito ?? -1,
        columnaCredito: detalle.delimitado.columnaCredito ?? -1,
        columnaTipoMovimiento: detalle.delimitado.columnaTipoMovimiento ?? -1,
        debitoYCreditoSeparados: detalle.delimitado.debitoYCreditoSeparados ?? false
      });
    }
  }

  eliminar(config: ConfiguracionExtracto): void {
    this.api.eliminarConfiguracion(config.id).subscribe({
      next: () => {
        this.configuraciones = this.configuraciones.filter(c => c.id !== config.id);
      },
      error: err => {
        this.errorGuardar = err.error?.message ?? 'Error al eliminar la configuración';
      }
    });
  }

  resetForms(): void {
    this.editandoId = null;
    this.bancoForm.reset();
    this.nombreForm.reset();
    this.tipoForm.reset();
    this.anchoFijoColumnas.clear();
    this.detalleForm.reset({
      tipoOrigen: 'DELIMITADO',
      separador: ',', filasASaltar: 0, columnaFecha: 0, formatoFecha: 'dd/MM/yyyy',
      columnaDescripcion: 1, columnaReferencia: -1, columnaMonto: 2, columnaDebito: -1, columnaCredito: -1,
      columnaTipoMovimiento: -1, debitoYCreditoSeparados: false, encoding: 'UTF-8', numeroHoja: 0,
      factorMonto: 1, separadorMiles: '.', separadorDecimales: ',',
      convencionSigno: 'SUFIJO', anchoFijoFormatoFecha: 'dd/MM/yyyy', anchoFijoInvertir: false,
      continuacionHabilitada: false, continuacionCampoAncla: 'dia', continuacionCampoDestino: 'descripcion',
      cuadreHabilitada: false, cuadreEtiquetaSaldoAnterior: '', cuadreEtiquetaCreditos: '',
      cuadreEtiquetaDebitos: '', cuadreEtiquetaSaldoFinal: ''
    });
    this.cuentasForm.reset({ aplicaParaTodasLasCuentas: true, idsCuentas: [] });
    this.tipoArchivo = null;
    this.lineaMuestra = '';
    this.periodoPrueba = '';
    this.limpiarResultadoPrueba();
  }

  canManage(): boolean {
    return this.auth.hasRole('CONTADOR', 'ADMIN');
  }
}
