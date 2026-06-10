package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.ConfiguracionDetalleDTO;
import com.conciliacion.bancaria.adapter.in.web.dto.ConfiguracionExtractoRequest;
import com.conciliacion.bancaria.adapter.in.web.dto.ConfiguracionExtractoResponse;
import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.port.in.ConfiguracionExtractoUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/configuraciones-extracto")
@RequiredArgsConstructor
public class ConfiguracionExtractoController {

    private final ConfiguracionExtractoUseCase configuracionExtractoUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ConfiguracionExtractoResponse>> crear(
            @Valid @RequestBody ConfiguracionExtractoRequest request) {
        ConfiguracionExtracto domain = toDomain(request);
        ConfiguracionExtracto creada = configuracionExtractoUseCase.crear(domain);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Configuración de extracto creada", toResponse(creada)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ConfiguracionExtractoResponse>> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ConfiguracionExtractoRequest request) {
        ConfiguracionExtracto domain = toDomain(request);
        ConfiguracionExtracto actualizada = configuracionExtractoUseCase.actualizar(id, domain);
        return ResponseEntity.ok(ApiResponse.ok("Configuración de extracto actualizada", toResponse(actualizada)));
    }

    @GetMapping("/banco/{bancoId}")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<ConfiguracionExtractoResponse>>> listarPorBanco(
            @PathVariable Long bancoId) {
        List<ConfiguracionExtractoResponse> lista = configuracionExtractoUseCase.listarPorBanco(bancoId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<ConfiguracionExtractoResponse>> obtenerPorId(
            @PathVariable Long id) {
        ConfiguracionExtracto config = configuracionExtractoUseCase.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(config)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        configuracionExtractoUseCase.eliminar(id);
        return ResponseEntity.ok(ApiResponse.ok("Configuración de extracto eliminada", null));
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ConfiguracionExtracto toDomain(ConfiguracionExtractoRequest req) {
        String detalleJson = null;
        if (req.getConfiguracionDetalle() != null) {
            try {
                detalleJson = objectMapper.writeValueAsString(req.getConfiguracionDetalle());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Error al serializar configuracionDetalle", e);
            }
        }
        return ConfiguracionExtracto.builder()
                .idBanco(req.getIdBanco())
                .nombre(req.getNombre())
                .tipoArchivo(req.getTipoArchivo())
                .aplicaParaTodasLasCuentas(req.isAplicaParaTodasLasCuentas())
                .idsCuentas(req.getIdsCuentas())
                .configuracionDetalle(detalleJson)
                .activo(true)
                .build();
    }

    private ConfiguracionExtractoResponse toResponse(ConfiguracionExtracto c) {
        return ConfiguracionExtractoResponse.builder()
                .id(c.getId())
                .idBanco(c.getIdBanco())
                .nombreBanco(c.getNombreBanco())
                .nombre(c.getNombre())
                .tipoArchivo(c.getTipoArchivo())
                .aplicaParaTodasLasCuentas(c.isAplicaParaTodasLasCuentas())
                .idsCuentas(c.getIdsCuentas())
                .configuracionDetalle(c.getConfiguracionDetalle())
                .activo(c.getActivo())
                .fechaCreacion(c.getFechaCreacion())
                .fechaModificacion(c.getFechaModificacion())
                .build();
    }
}
