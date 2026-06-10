package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.BancoRequest;
import com.conciliacion.bancaria.adapter.in.web.dto.BancoResponse;
import com.conciliacion.bancaria.domain.model.Banco;
import com.conciliacion.bancaria.domain.port.in.BancoUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bancos")
@RequiredArgsConstructor
public class BancoController {

    private final BancoUseCase bancoUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<BancoResponse>>> listar() {
        List<BancoResponse> lista = bancoUseCase.listarActivos().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<BancoResponse>> crear(
            @Valid @RequestBody BancoRequest request) {
        Banco banco = bancoUseCase.crear(request.getNombre(), request.getCodigo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Banco creado", toResponse(banco)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> eliminar(@PathVariable Long id) {
        bancoUseCase.desactivar(id);
        return ResponseEntity.ok(ApiResponse.ok("Banco eliminado", null));
    }

    private BancoResponse toResponse(Banco b) {
        return BancoResponse.builder()
                .id(b.getId())
                .nombre(b.getNombre())
                .codigo(b.getCodigo())
                .activo(b.getActivo())
                .tsCreacion(b.getTsCreacion())
                .build();
    }
}
