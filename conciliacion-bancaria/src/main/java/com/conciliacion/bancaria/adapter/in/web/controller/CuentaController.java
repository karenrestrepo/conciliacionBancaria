package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.CuentaRequest;
import com.conciliacion.bancaria.adapter.in.web.dto.CuentaResponse;
import com.conciliacion.bancaria.domain.model.Cuenta;
import com.conciliacion.bancaria.domain.port.in.CuentaUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaUseCase cuentaUseCase;

    // ── Cuentas de un banco específico ──────────────────────────────────────

    @GetMapping("/api/v1/bancos/{idBanco}/cuentas")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<CuentaResponse>>> listarPorBanco(
            @PathVariable Long idBanco) {
        List<CuentaResponse> lista = cuentaUseCase.listarPorBanco(idBanco).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    @PostMapping("/api/v1/bancos/{idBanco}/cuentas")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<CuentaResponse>> crear(
            @PathVariable Long idBanco,
            @Valid @RequestBody CuentaRequest request) {
        // Ignoramos request.idBanco si viene en el body; usamos el path param
        Cuenta cuenta = cuentaUseCase.crear(
                idBanco,
                request.getNumeroCuenta(),
                request.getTipo(),
                request.getDescripcion(),
                Boolean.TRUE.equals(request.getAuxiliarConjunto()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Cuenta creada", toResponse(cuenta)));
    }

    @PatchMapping("/api/v1/cuentas/{id}/estado")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<CuentaResponse>> cambiarEstado(
            @PathVariable Long id,
            @RequestParam boolean activo) {
        Cuenta cuenta = cuentaUseCase.cambiarEstado(id, activo);
        return ResponseEntity.ok(ApiResponse.ok(
                activo ? "Cuenta activada" : "Cuenta desactivada", toResponse(cuenta)));
    }

    @DeleteMapping("/api/v1/cuentas/{id}")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        cuentaUseCase.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok("Cuenta eliminada", null));
    }

    // ── Todas las cuentas activas (para dropdowns) ────────────────────────

    @GetMapping("/api/v1/cuentas")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<CuentaResponse>>> listarTodas() {
        Long empresaId = empresaIdActual();
        List<CuentaResponse> lista = cuentaUseCase.listarActivasPorEmpresa(empresaId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    private Long empresaIdActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Long id) return id;
        return null;
    }

    // ── Mapper ────────────────────────────────────────────────────────────

    private CuentaResponse toResponse(Cuenta c) {
        return CuentaResponse.builder()
                .id(c.getId())
                .idBanco(c.getIdBanco())
                .nombreBanco(c.getNombreBanco())
                .numeroCuenta(c.getNumeroCuenta())
                .tipo(c.getTipo())
                .descripcion(c.getDescripcion())
                .activo(c.getActivo())
                .auxiliarConjunto(Boolean.TRUE.equals(c.getAuxiliarConjunto()))
                .tsCreacion(c.getTsCreacion())
                .build();
    }
}
