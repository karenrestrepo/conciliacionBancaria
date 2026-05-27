import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { ApiResponse, Conciliacion, Sugerencia, JobStatus, MetricasResumen } from './models';

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

  crearConciliacion(periodo: string): Observable<ApiResponse<Conciliacion>> {
    return this.http.post<ApiResponse<Conciliacion>>(
      `${this.API}/conciliaciones`, { periodo }, { headers: this.headers() });
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

  rechazarSugerencia(idConciliacion: number, idSugerencia: number): Observable<ApiResponse<Sugerencia>> {
    return this.http.post<ApiResponse<Sugerencia>>(
      `${this.API}/conciliaciones/${idConciliacion}/sugerencias/${idSugerencia}/rechazar`,
      {}, { headers: this.headers() });
  }

  // Métricas
  obtenerMetricas(): Observable<ApiResponse<MetricasResumen>> {
    return this.http.get<ApiResponse<MetricasResumen>>(
      `${this.API}/metricas/resumen`, { headers: this.headers() });
  }
}
