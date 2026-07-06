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
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort movimientoRepo;
    private final com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort conciliacionRepo;

    // ── Iniciar conciliación (AUXILIAR, CONTADOR) ─────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ConciliacionResponse>> iniciar(
            @Valid @RequestBody ConciliacionRequest request,
            @AuthenticationPrincipal String email) {

        Conciliacion conciliacion = conciliacionUseCase.iniciar(
                request.getPeriodo(), 1L, request.getIdCuenta(), empresaIdActual());
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
                .listarPorEmpresa(empresaIdActual()).stream()
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

    // ── Re-procesar motor (sin re-subir archivos) ─────────────────────────────

    @PostMapping("/{id}/reprocesar")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<String>> reprocesar(@PathVariable Long id) {
        String jobId = cargaCsvUseCase.reprocesarMotor(id);
        return ResponseEntity.ok(ApiResponse.ok("Motor re-iniciado", jobId));
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

    // ── Aceptar sugerencias en lote ───────────────────────────────────────────

    @PostMapping("/{id}/sugerencias/aceptar-lote")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<SugerenciaResponse>>> aceptarLote(
            @PathVariable Long id,
            @RequestBody java.util.List<Long> ids) {
        java.util.List<SugerenciaResponse> resultado = revisionUseCase.aceptarLote(ids, 1L)
                .stream().map(this::toSugerenciaResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok("Sugerencias aceptadas: " + resultado.size(), resultado));
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

    // ── Arrastrar partida a próximo mes ───────────────────────────────────────

    @PostMapping("/{id}/partidas/{idPartida}/arrastrar")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<PartidaResponse>> arrastrar(
            @PathVariable Long id,
            @PathVariable Long idPartida,
            @Valid @RequestBody ArrastrarPartidaRequest request) {
        PartidaConciliatoria arrastrada = cierreUseCase.arrastrarPartida(
                idPartida, request.getPeriodoDestino(), 1L);
        return ResponseEntity.ok(ApiResponse.ok("Partida marcada para próximo mes", toPartidaResponse(arrastrada)));
    }

    // ── Cruzar partidas manualmente ───────────────────────────────────────────

    @PostMapping("/{id}/partidas/cruzar")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<PartidaResponse>>> cruzarPartidas(
            @PathVariable Long id,
            @Valid @RequestBody CruzarPartidasRequest request) {
        List<PartidaResponse> resultado = cierreUseCase
                .cruzarPartidas(id, request.getIdOrigen(), request.getIdsDestino(), request.getTipo()).stream()
                .map(this::toPartidaResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Partidas cruzadas", resultado));
    }

    // ── Partidas históricas (de conciliaciones anteriores del mismo cuenta) ──

    @GetMapping("/{id}/partidas-historicas")
    @PreAuthorize("hasAnyRole('CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<PartidaResponse>>> partidasHistoricas(
            @PathVariable Long id) {
        java.util.List<PartidaResponse> lista = cierreUseCase.listarPartidasHistoricas(id)
                .stream().map(this::toPartidaResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    // ── Gastos bancarios agrupados ────────────────────────────────────────────

    @GetMapping("/{id}/gastos-bancarios-agrupados")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<MovimientoAgrupadoResponse>>> gastosAgrupados(
            @PathVariable Long id) {
        java.util.List<MovimientoAgrupadoResponse> lista = movimientoRepo
                .buscarBancariosAgrupadosPorConciliacion(id)
                .stream()
                .map(m -> MovimientoAgrupadoResponse.builder()
                        .id(m.getId())
                        .descripcion(m.getDescripcion())
                        .monto(m.getMonto())
                        .fecha(m.getFecha())
                        .tipo(m.getTipo())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    // ── Cerrar conciliación ───────────────────────────────────────────────────

    @PostMapping("/{id}/cerrar")
    @PreAuthorize("hasRole('CONTADOR')")
    public ResponseEntity<ApiResponse<ConciliacionResponse>> cerrar(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                toResponse(cierreUseCase.cerrar(id, 1L))));
    }

    // ── Helper de seguridad ───────────────────────────────────────────────────

    private Long empresaIdActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long id) return id;
        return null;
    }

    // ── Mappers locales ───────────────────────────────────────────────────────

    private ConciliacionResponse toResponse(Conciliacion c) {
        return ConciliacionResponse.builder()
                .id(c.getId())
                .periodo(c.getPeriodo())
                .idCuenta(c.getIdCuenta())
                .numeroCuenta(c.getNumeroCuenta())
                .tipoCuenta(c.getTipoCuenta())
                .idBanco(c.getIdBanco())
                .nombreBanco(c.getNombreBanco())
                .estado(c.getEstado().name())
                .idUsuarioCreador(c.getIdUsuarioCreador())
                .idUsuarioAprobador(c.getIdUsuarioAprobador())
                .tsCreacion(c.getTsCreacion())
                .tsCierre(c.getTsCierre())
                .saldoExtracto(c.getSaldoExtracto())
                .saldoAuxiliar(c.getSaldoAuxiliar())
                .diferenciaSaldo(c.getDiferenciaSaldo())
                .auxiliarConjunto(c.getId() != null ? conciliacionRepo.esAuxiliarConjunto(c.getId()) : false)
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
                .periodoArrastre(p.getPeriodoArrastre())
                .fechaMovimiento(p.getFechaMovimiento())
                .descripcionMovimiento(p.getDescripcionMovimiento())
                .montoMovimiento(p.getMontoMovimiento())
                .tipoMovimiento(p.getTipoMovimiento())
                .esHistorica(p.isEsHistorica())
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