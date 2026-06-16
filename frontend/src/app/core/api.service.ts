import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiResponse, Banco, Cuenta, TipoCuenta, Conciliacion, Sugerencia, JobStatus, MetricasResumen, ConfiguracionExtracto, GastoBancario, MovimientoAgrupado } from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly API = 'http://localhost:8080/api/v1';

  constructor(private http: HttpClient, private auth: AuthService) {}

  private headers(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.auth.getToken()}` });
  }

  // Conciliaciones
  listarConciliaciones(): Observable<ApiResponse<Conciliacion[]>> {
    return this.http.get<ApiResponse<Conciliacion[]>>(
      `${this.API}/conciliaciones`, { headers: this.headers() });
  }

  crearConciliacion(periodo: string, idCuenta: number): Observable<ApiResponse<Conciliacion>> {
    return this.http.post<ApiResponse<Conciliacion>>(
      `${this.API}/conciliaciones`, { periodo, idCuenta }, { headers: this.headers() });
  }

  // Bancos
  listarBancos(): Observable<ApiResponse<Banco[]>> {
    return this.http.get<ApiResponse<Banco[]>>(
      `${this.API}/bancos`, { headers: this.headers() });
  }

  crearBanco(nombre: string, codigo: string): Observable<ApiResponse<Banco>> {
    return this.http.post<ApiResponse<Banco>>(
      `${this.API}/bancos`, { nombre, codigo }, { headers: this.headers() });
  }

  eliminarBanco(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(
      `${this.API}/bancos/${id}`, { headers: this.headers() });
  }

  // Cuentas
  listarCuentasPorBanco(idBanco: number): Observable<ApiResponse<Cuenta[]>> {
    return this.http.get<ApiResponse<Cuenta[]>>(
      `${this.API}/bancos/${idBanco}/cuentas`, { headers: this.headers() });
  }

  listarTodasCuentas(): Observable<ApiResponse<Cuenta[]>> {
    return this.http.get<ApiResponse<Cuenta[]>>(
      `${this.API}/cuentas`, { headers: this.headers() });
  }

  crearCuenta(idBanco: number, numeroCuenta: string, tipo: TipoCuenta, descripcion: string): Observable<ApiResponse<Cuenta>> {
    return this.http.post<ApiResponse<Cuenta>>(
      `${this.API}/bancos/${idBanco}/cuentas`,
      { numeroCuenta, tipo, descripcion },
      { headers: this.headers() });
  }

  cambiarEstadoCuenta(id: number, activo: boolean): Observable<ApiResponse<Cuenta>> {
    return this.http.patch<ApiResponse<Cuenta>>(
      `${this.API}/cuentas/${id}/estado?activo=${activo}`, {}, { headers: this.headers() });
  }

  eliminarCuenta(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(
      `${this.API}/cuentas/${id}`, { headers: this.headers() });
  }

  obtenerConciliacion(id: number): Observable<ApiResponse<Conciliacion>> {
    return this.http.get<ApiResponse<Conciliacion>>(
      `${this.API}/conciliaciones/${id}`, { headers: this.headers() });
  }

  cargarExtracto(id: number, archivo: File): Observable<ApiResponse<string>> {
    const form = new FormData();
    form.append('archivo', archivo);
    return this.http.post<ApiResponse<string>>(
      `${this.API}/conciliaciones/${id}/extracto`, form, { headers: new HttpHeaders({ Authorization: `Bearer ${this.auth.getToken()}` }) });
  }

  cargarAuxiliar(id: number, archivo: File): Observable<ApiResponse<string>> {
    const form = new FormData();
    form.append('archivo', archivo);
    return this.http.post<ApiResponse<string>>(
      `${this.API}/conciliaciones/${id}/auxiliar`, form, { headers: new HttpHeaders({ Authorization: `Bearer ${this.auth.getToken()}` }) });
  }

  jobStatus(jobId: string): Observable<ApiResponse<JobStatus>> {
    return this.http.get<ApiResponse<JobStatus>>(
      `${this.API}/conciliaciones/jobs/${jobId}/status`, { headers: this.headers() });
  }

  cruzarPartidas(id: number, idOrigen: number, idsDestino: number[], tipo: string): Observable<ApiResponse<any[]>> {
    return this.http.post<ApiResponse<any[]>>(
      `${this.API}/conciliaciones/${id}/partidas/cruzar`, { idOrigen, idsDestino, tipo }, { headers: this.headers() });
  }

  reprocesarMotor(id: number): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(
      `${this.API}/conciliaciones/${id}/reprocesar`, {}, { headers: this.headers() });
  }

  pasarARevision(id: number): Observable<ApiResponse<Conciliacion>> {
    return this.http.post<ApiResponse<Conciliacion>>(
      `${this.API}/conciliaciones/${id}/revision`, {}, { headers: this.headers() });
  }

  cerrarConciliacion(id: number): Observable<ApiResponse<Conciliacion>> {
    return this.http.post<ApiResponse<Conciliacion>>(
      `${this.API}/conciliaciones/${id}/cerrar`, {}, { headers: this.headers() });
  }

  listarPartidas(id: number): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(
      `${this.API}/conciliaciones/${id}/partidas`, { headers: this.headers() });
  }

  justificarPartida(idConciliacion: number, idPartida: number, justificacion: string, fecha: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(
      `${this.API}/conciliaciones/${idConciliacion}/partidas/${idPartida}/justificar`,
      { justificacion, fecha }, { headers: this.headers() });
  }

  arrastrarPartida(idConciliacion: number, idPartida: number, periodoDestino: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(
      `${this.API}/conciliaciones/${idConciliacion}/partidas/${idPartida}/arrastrar`,
      { periodoDestino }, { headers: this.headers() });
  }

  listarPartidasHistoricas(idConciliacion: number): Observable<ApiResponse<any[]>> {
    return this.http.get<ApiResponse<any[]>>(
      `${this.API}/conciliaciones/${idConciliacion}/partidas-historicas`, { headers: this.headers() });
  }

  // Sugerencias
  obtenerSugerencias(id: number): Observable<ApiResponse<Sugerencia[]>> {
    return this.http.get<ApiResponse<Sugerencia[]>>(
      `${this.API}/conciliaciones/${id}/sugerencias`, { headers: this.headers() });
  }

  aceptarSugerencia(idConciliacion: number, idSugerencia: number): Observable<ApiResponse<Sugerencia>> {
    return this.http.post<ApiResponse<Sugerencia>>(
      `${this.API}/conciliaciones/${idConciliacion}/sugerencias/${idSugerencia}/aceptar`,
      {}, { headers: this.headers() });
  }

  aceptarSugerenciasLote(idConciliacion: number, ids: number[]): Observable<ApiResponse<Sugerencia[]>> {
    return this.http.post<ApiResponse<Sugerencia[]>>(
      `${this.API}/conciliaciones/${idConciliacion}/sugerencias/aceptar-lote`,
      ids, { headers: this.headers() });
  }

  rechazarSugerencia(idConciliacion: number, idSugerencia: number): Observable<ApiResponse<Sugerencia>> {
    return this.http.post<ApiResponse<Sugerencia>>(
      `${this.API}/conciliaciones/${idConciliacion}/sugerencias/${idSugerencia}/rechazar`,
      {}, { headers: this.headers() });
  }

  listarGastosAgrupadosConciliacion(idConciliacion: number): Observable<ApiResponse<MovimientoAgrupado[]>> {
    return this.http.get<ApiResponse<MovimientoAgrupado[]>>(
      `${this.API}/conciliaciones/${idConciliacion}/gastos-bancarios-agrupados`, { headers: this.headers() });
  }

  // Gastos bancarios (agrupación de cargos recurrentes del extracto)
  listarGastosBancarios(idCuenta: number): Observable<ApiResponse<GastoBancario[]>> {
    return this.http.get<ApiResponse<GastoBancario[]>>(
      `${this.API}/cuentas/${idCuenta}/gastos-bancarios`, { headers: this.headers() });
  }

  agregarGastoBancario(idCuenta: number, descripcion: string): Observable<ApiResponse<GastoBancario>> {
    return this.http.post<ApiResponse<GastoBancario>>(
      `${this.API}/cuentas/${idCuenta}/gastos-bancarios`,
      { descripcion }, { headers: this.headers() });
  }

  eliminarGastoBancario(idCuenta: number, id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(
      `${this.API}/cuentas/${idCuenta}/gastos-bancarios/${id}`, { headers: this.headers() });
  }

  // Métricas
  obtenerMetricas(): Observable<ApiResponse<MetricasResumen>> {
    return this.http.get<ApiResponse<MetricasResumen>>(
      `${this.API}/metricas/resumen`, { headers: this.headers() });
  }

  // Configuraciones de extracto
  listarConfiguracionesPorBanco(idBanco: number): Observable<ApiResponse<ConfiguracionExtracto[]>> {
    return this.http.get<ApiResponse<ConfiguracionExtracto[]>>(
      `${this.API}/configuraciones-extracto/banco/${idBanco}`, { headers: this.headers() });
  }

  crearConfiguracion(req: any): Observable<ApiResponse<ConfiguracionExtracto>> {
    return this.http.post<ApiResponse<ConfiguracionExtracto>>(
      `${this.API}/configuraciones-extracto`, req, { headers: this.headers() });
  }

  actualizarConfiguracion(id: number, req: any): Observable<ApiResponse<ConfiguracionExtracto>> {
    return this.http.put<ApiResponse<ConfiguracionExtracto>>(
      `${this.API}/configuraciones-extracto/${id}`, req, { headers: this.headers() });
  }

  eliminarConfiguracion(id: number): Observable<ApiResponse<any>> {
    return this.http.delete<ApiResponse<any>>(
      `${this.API}/configuraciones-extracto/${id}`, { headers: this.headers() });
  }
}
