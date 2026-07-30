export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  email: string;
  rol: string;
  empresaId: number | null;
  nombreEmpresa: string | null;
  permisos: string[];
  expiresIn: number;
}

export type TipoIdentificacion = 'CEDULA' | 'NIT';

export interface VerificarEmpresaRequest {
  tipoIdentificacion: TipoIdentificacion;
  numeroIdentificacion: string;
  digitoVerificacion?: string;
}

export interface RegistroRequest {
  tipoIdentificacion: TipoIdentificacion;
  numeroIdentificacion: string;
  digitoVerificacion?: string;
  nombreEmpresa: string;
  nombreAdmin: string;
  emailAdmin: string;
  passwordAdmin: string;
}

export type RolUsuario = 'AUXILIAR' | 'CONTADOR' | 'FINANZAS' | 'ADMIN';

export type Permiso =
  | 'VER_CONCILIACIONES'
  | 'CREAR_CONCILIACION'
  | 'APROBAR_CONCILIACION'
  | 'CERRAR_CONCILIACION'
  | 'VER_MOVIMIENTOS'
  | 'GESTIONAR_BANCOS'
  | 'GESTIONAR_EXTRACTOS'
  | 'CARGAR_ARCHIVOS'
  | 'GESTIONAR_USUARIOS';

export interface UsuarioResponse {
  id: number;
  nombre: string;
  email: string;
  rol: RolUsuario;
  activo: boolean;
  tsCreacion: string;
  permisos: Permiso[];
}

export interface UsuarioCreateRequest {
  nombre: string;
  email: string;
  password: string;
  rol: RolUsuario;
  permisos: Permiso[];
}

export interface UsuarioUpdateRequest {
  nombre: string;
  email: string;
  password?: string;
  rol: RolUsuario;
  activo: boolean;
  permisos: Permiso[];
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export type TipoCuenta = 'CORRIENTE' | 'AHORRO' | 'FIDUCIARIA' | 'TARJETA_CREDITO' | 'OTRA';

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
  auxiliarConjunto: boolean;
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
  auxiliarConjunto: boolean | null;
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

/** Resultado de recargar el libro auxiliar: nuevos renglones, bajas por anulación, y cuántas de esas requirieron revertir una conciliación. */
export interface ResumenCargaAuxiliar {
  jobId: string | null;
  nuevos: number;
  anulados: number;
  revertidos: number;
}

export type TipoArchivoExtracto = 'CSV' | 'TXT' | 'XLS' | 'XLSX' | 'PDF';

export interface ConfiguracionDetalle {
  separador?: string;
  filasASaltar?: number;
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
  factorMonto?: number;
  separadorMiles?: string;
  separadorDecimales?: string;
  /** Solo para tipoArchivo=TXT: 'DELIMITADO' (CSV genérico) o 'ANCHO_FIJO' (ej. Davivienda). */
  formatoTxt?: 'DELIMITADO' | 'ANCHO_FIJO';
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

export interface MovimientoAgrupado {
  id: number;
  descripcion: string;
  monto: number;
  fecha: string;
  tipo: string;
}

export interface GrupoGastoBancario {
  descripcion: string;
  count: number;
  total: number;
  tipo: string;
  movimientos: MovimientoAgrupado[];
}

export interface GastoBancario {
  id: number;
  idCuenta: number;
  descripcion: string;
  activo: boolean;
  fechaCreacion: string;
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
