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
  ultimosDigitosTarjeta: string | null;
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

/** Layout estructural del extracto — determina qué estrategia de parseo usa el motor genérico. */
export type TipoOrigenExtracto = 'DELIMITADO' | 'ANCHO_FIJO' | 'EXCEL';

/** Convención de signo para un extracto de ancho fijo. */
export type ConvencionSigno = 'SUFIJO' | 'PREFIJO' | 'COLUMNAS_SEPARADAS' | 'COLUMNA_TIPO';

/** Campo lógico que puede ocupar una columna de ancho fijo. */
export type CampoAnchoFijo =
  'dia' | 'mes' | 'anio' | 'fecha' | 'descripcion' | 'monto' | 'debito' | 'credito' | 'signo' | 'tipo';

/** Una columna definida por posición de caracter (1-based, inclusive) — sin regex. */
export interface ColumnaPosicion {
  campo: CampoAnchoFijo;
  inicio: number;
  fin: number;
}

export interface ConfigDelimitado {
  separador?: string;
  filasASaltar?: number;
  columnaFecha?: number;
  formatoFecha?: string;
  columnaDescripcion?: number;
  columnaReferencia?: number;
  columnaMonto?: number;
  columnaDebito?: number;
  columnaCredito?: number;
  columnaTipoMovimiento?: number;
  debitoYCreditoSeparados?: boolean;
}

export interface ConfigExcel {
  numeroHoja?: number;
  filasASaltar?: number;
  columnaFecha?: number;
  formatoFecha?: string;
  columnaDescripcion?: number;
  columnaReferencia?: number;
  columnaMonto?: number;
  columnaDebito?: number;
  columnaCredito?: number;
  debitoYCreditoSeparados?: boolean;
}

export interface ConfigAnchoFijo {
  columnas: ColumnaPosicion[];
  convencionSigno: ConvencionSigno;
  formatoFecha?: string;
  /** Invierte DEBITO/CREDITO (tarjetas de crédito: "+" es cargo, "-" es pago). No aplica a columnas separadas. */
  invertir?: boolean;
}

export interface ReglaContinuacion {
  habilitada: boolean;
  campoAncla?: string;
  campoDestino?: string;
}

export interface ReglaCuadre {
  habilitada: boolean;
  etiquetaSaldoAnterior?: string;
  etiquetaCreditos?: string;
  etiquetaDebitos?: string;
  etiquetaSaldoFinal?: string;
  tolerancia?: number;
}

/** Schema tipado único de configuración de extracto — el mismo objeto para editar en el wizard y para probar/guardar. */
export interface ConfiguracionExtractoDetalle {
  tipoOrigen: TipoOrigenExtracto | null;
  encoding?: string;
  factorMonto?: number;
  separadorMiles?: string;
  separadorDecimales?: string;
  delimitado?: ConfigDelimitado;
  anchoFijo?: ConfigAnchoFijo;
  excel?: ConfigExcel;
  continuacion?: ReglaContinuacion;
  cuadre?: ReglaCuadre;
}

export interface ResultadoCuadre {
  habilitada: boolean;
  saldoAnterior?: number;
  creditos?: number;
  debitos?: number;
  saldoFinal?: number;
  saldoCalculado?: number;
  diferencia?: number;
  cuadra: boolean;
  advertencias: string[];
}

export interface MovimientoPreview {
  fecha: string;
  descripcion: string;
  monto: number;
  tipo: string;
}

export interface PruebaConfiguracionResultado {
  movimientos: MovimientoPreview[];
  totalMovimientos: number;
  cuadre: ResultadoCuadre;
  advertencias: string[];
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
