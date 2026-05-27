package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.*;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.port.in.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conciliaciones")
@RequiredArgsConstructor
public class ConciliacionController {

    private final ConciliacionUseCase conciliacionUseCase;
    private final CargaCsvUseCase cargaCsvUseCase;
    private final RevisionUseCase revisionUseCase;
    private final CierreUseCase cierreUseCase;
    private final JobStatusUseCase jobStatusUseCase;

    // ── Iniciar conciliación (AUXILIAR, CONTADOR) ─────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ConciliacionResponse>> iniciar(
            @Valid @RequestBody ConciliacionRequest request,
            @AuthenticationPrincipal String email) {

        // Por ahora usamos id=1 del admin; en siguiente paso extraemos del token
        Conciliacion conciliacion = conciliacionUseCase.iniciar(
                request.getPeriodo(), 1L);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Conciliación iniciada", toResponse(conciliacion)));
    }

    // ── Obtener por id ────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<ConciliacionResponse>> obtener(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.ok(toResponse(conciliacionUseCase.obtenerPorId(id))));
    }

    // ── Listar mis conciliaciones ─────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<ConciliacionResponse>>> listar() {
        List<ConciliacionResponse> lista = conciliacionUseCase
                .listarTodas().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    // ── Cargar extracto bancario CSV ──────────────────────────────────────────

    @PostMapping("/{id}/extracto")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<String>> cargarExtracto(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "banco", defaultValue = "GENERICO") String banco) {

        String jobId = cargaCsvUseCase.cargarExtractoBancario(id, archivo, banco);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("Motor iniciado", jobId));
    }

    // ── Cargar libro auxiliar CSV ─────────────────────────────────────────────

    @PostMapping("/{id}/auxiliar")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<String>> cargarAuxiliar(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo) {

        cargaCsvUseCase.cargarLibroAuxiliar(id, archivo);
        return ResponseEntity.ok(ApiResponse.ok("Libro auxiliar cargado", null));
    }

    // ── Polling del motor ─────────────────────────────────────────────────────

    @GetMapping("/jobs/{jobId}/status")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<JobStatusUseCase.JobStatus>> jobStatus(
            @PathVariable String jobId) {
        return ResponseEntity.ok(
                ApiResponse.ok(jobStatusUseCase.obtenerEstado(jobId)));
    }

    // ── Sugerencias ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/sugerencias")
    @PreAuthorize("hasAnyRole('CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<SugerenciaResponse>>> sugerencias(
            @PathVariable Long id) {
        List<SugerenciaResponse> lista = revisionUseCase
                .obtenerSugerencias(id).stream()
                .map(this::toSugerenciaResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    // ── Aceptar sugerencia ────────────────────────────────────────────────────

    @PostMapping("/{id}/sugerencias/{idSugerencia}/aceptar")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<SugerenciaResponse>> aceptar(
            @PathVariable Long id,
            @PathVariable Long idSugerencia) {
        return ResponseEntity.ok(ApiResponse.ok(
                toSugerenciaResponse(
                        revisionUseCase.aceptarSugerencia(idSugerencia, 1L))));
    }

    // ── Rechazar sugerencia ───────────────────────────────────────────────────

    @PostMapping("/{id}/sugerencias/{idSugerencia}/rechazar")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<SugerenciaResponse>> rechazar(
            @PathVariable Long id,
            @PathVariable Long idSugerencia) {
        return ResponseEntity.ok(ApiResponse.ok(
                toSugerenciaResponse(
                        revisionUseCase.rechazarSugerencia(idSugerencia, 1L))));
    }

    // ── Pasar a revisión ──────────────────────────────────────────────────────

    @PostMapping("/{id}/revision")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ConciliacionResponse>> pasarARevision(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                toResponse(conciliacionUseCase.pasarARevision(id))));
    }

    // ── Listar partidas ───────────────────────────────────────────────────────

    @GetMapping("/{id}/partidas")
    @PreAuthorize("hasAnyRole('CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<PartidaResponse>>> partidas(
            @PathVariable Long id) {
        List<PartidaResponse> lista = cierreUseCase.listarPartidas(id).stream()
                .map(this::toPartidaResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    // ── Justificar partida ────────────────────────────────────────────────────

    @PostMapping("/{id}/partidas/{idPartida}/justificar")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<String>> justificar(
            @PathVariable Long id,
            @PathVariable Long idPartida,
            @Valid @RequestBody JustificarPartidaRequest request) {
        cierreUseCase.justificarPartida(idPartida,
                request.getJustificacion(), request.getFecha());
        return ResponseEntity.ok(ApiResponse.ok("Partida justificada", null));
    }

    // ── Cerrar conciliación ───────────────────────────────────────────────────

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasRole('CONTADOR')")
    public ResponseEntity<ApiResponse<ConciliacionResponse>> cerrar(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                toResponse(cierreUseCase.cerrar(id, 1L))));
    }

    // ── Mappers locales ───────────────────────────────────────────────────────

    private ConciliacionResponse toResponse(Conciliacion c) {
        return ConciliacionResponse.builder()
                .id(c.getId())
                .periodo(c.getPeriodo())
                .estado(c.getEstado().name())
                .idUsuarioCreador(c.getIdUsuarioCreador())
                .idUsuarioAprobador(c.getIdUsuarioAprobador())
                .tsCreacion(c.getTsCreacion())
                .tsCierre(c.getTsCierre())
                .saldoExtracto(c.getSaldoExtracto())
                .saldoAuxiliar(c.getSaldoAuxiliar())
                .diferenciaSaldo(c.getDiferenciaSaldo())
                .build();
    }

    private PartidaResponse toPartidaResponse(PartidaConciliatoria p) {
        return PartidaResponse.builder()
                .id(p.getId())
                .idConciliacion(p.getIdConciliacion())
                .idMovimiento(p.getIdMovimiento())
                .tipoOrigen(p.getTipoOrigen())
                .estado(p.getEstado())
                .justificacion(p.getJustificacion())
                .fechaJustificacion(p.getFechaJustificacion())
                .build();
    }

    private SugerenciaResponse toSugerenciaResponse(
            com.conciliacion.bancaria.domain.model.Sugerencia s) {
        return SugerenciaResponse.builder()
                .id(s.getId())
                .idConciliacion(s.getIdConciliacion())
                .confianza(s.getConfianza())
                .criterio(s.getCriterio())
                .estado(s.getEstado().name())
                .idMovBancario(s.getMovimientoBancario().getId())
                .fechaBancario(s.getMovimientoBancario().getFecha())
                .descripcionBancario(s.getMovimientoBancario().getDescripcion())
                .montoBancario(s.getMovimientoBancario().getMonto())
                .tipoBancario(s.getMovimientoBancario().getTipo())
                .idMovContable(s.getMovimientoContable().getId())
                .fechaContable(s.getMovimientoContable().getFecha())
                .descripcionContable(s.getMovimientoContable().getDescripcion())
                .montoContable(s.getMovimientoContable().getMonto())
                .tipoContable(s.getMovimientoContable().getTipo())
                .build();
    }
}