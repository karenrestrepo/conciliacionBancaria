export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  email: string;
  rol: string;
  expiresIn: number;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export type TipoCuenta = 'CORRIENTE' | 'AHORRO' | 'FIDUCIARIA' | 'OTRA';

export interface Banco {
  id: number;
  nombre: string;
  codigo: string | null;
  activo: boolean;
  tsCreacion: string;
}

export interface Cuenta {
  id: number;
  idBanco: number;
  nombreBanco: string | null;
  numeroCuenta: string;
  tipo: TipoCuenta;
  descripcion: string | null;
  activo: boolean;
  tsCreacion: string;
}

export interface Conciliacion {
  id: number;
  periodo: string;
  idCuenta: number;
  numeroCuenta: string | null;
  tipoCuenta: string | null;
  idBanco: number | null;
  nombreBanco: string | null;
  estado: 'BORRADOR' | 'EN_REVISION' | 'CERRADA';
  idUsuarioCreador: number;
  idUsuarioAprobador: number | null;
  tsCreacion: string;
  tsCierre: string | null;
  saldoExtracto: number | null;
  saldoAuxiliar: number | null;
  diferenciaSaldo: number | null;
}

export interface Sugerencia {
  id: number;
  idConciliacion: number;
  confianza: number;
  criterio: string;
  estado: 'PENDIENTE_REVISION' | 'ACEPTADA' | 'RECHAZADA' | 'REASIGNADA';
  idMovBancario: number;
  fechaBancario: string;
  descripcionBancario: string;
  montoBancario: number;
  tipoBancario: string;
  idMovContable: number;
  fechaContable: string;
  descripcionContable: string;
  montoContable: number;
  tipoContable: string;
}

export interface JobStatus {
  jobId: string;
  estado: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  progreso: number;
  mensajeError: string | null;
}

export type TipoArchivoExtracto = 'CSV' | 'TXT' | 'XLS' | 'XLSX' | 'PDF';

export interface ConfiguracionDetalle {
  separador?: string;
  filasASaltar?: number;
  tieneEncabezado?: boolean;
  columnaFecha?: number;
  formatoFecha?: string;
  columnaDescripcion?: number;
  columnaReferencia?: number;
  columnaMonto?: number;
  columnaDebito?: number;
  columnaCredito?: number;
  debitoYCreditoSeparados?: boolean;
  encoding?: string;
  numeroHoja?: number;
}

export interface ConfiguracionExtracto {
  id: number;
  idBanco: number;
  nombreBanco: string | null;
  nombre: string;
  tipoArchivo: TipoArchivoExtracto;
  aplicaParaTodasLasCuentas: boolean;
  idsCuentas: number[];
  configuracionDetalle: string | null;
  activo: boolean;
  fechaCreacion: string;
  fechaModificacion: string | null;
}

export interface MetricasResumen {
  totalConciliaciones: number;
  enBorrador: number;
  enRevision: number;
  cerradas: number;
  totalMovimientosBancarios: number;
  totalMovimientosContables: number;
  totalSugerencias: number;
  sugerenciasAceptadas: number;
  sugerenciasRechazadas: number;
  sugerenciasPendientes: number;
  diferenciaPromedio: number;
}
