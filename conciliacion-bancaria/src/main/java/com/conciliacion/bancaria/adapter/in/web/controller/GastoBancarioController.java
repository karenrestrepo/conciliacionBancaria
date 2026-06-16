package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.GastoBancarioRequest;
import com.conciliacion.bancaria.adapter.in.web.dto.GastoBancarioResponse;
import com.conciliacion.bancaria.domain.model.ConfiguracionGastoBancario;
import com.conciliacion.bancaria.domain.port.in.GastoBancarioUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cuentas/{idCuenta}/gastos-bancarios")
@RequiredArgsConstructor
public class GastoBancarioController {

    private final GastoBancarioUseCase gastoBancarioUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<List<GastoBancarioResponse>>> listar(
            @PathVariable Long idCuenta) {
        List<GastoBancarioResponse> lista = gastoBancarioUseCase.listar(idCuenta)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok(lista));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<GastoBancarioResponse>> agregar(
            @PathVariable Long idCuenta,
            @Valid @RequestBody GastoBancarioRequest request) {
        GastoBancarioResponse resp = toResponse(
                gastoBancarioUseCase.agregar(idCuenta, request.getDescripcion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Descripción agregada", resp));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> eliminar(
            @PathVariable Long idCuenta,
            @PathVariable Long id) {
        gastoBancarioUseCase.eliminar(idCuenta, id);
        return ResponseEntity.ok(ApiResponse.ok("Descripción eliminada", null));
    }

    private GastoBancarioResponse toResponse(ConfiguracionGastoBancario c) {
        return GastoBancarioResponse.builder()
                .id(c.getId())
                .idCuenta(c.getIdCuenta())
                .descripcion(c.getDescripcion())
                .activo(c.isActivo())
                .fechaCreacion(c.getFechaCreacion())
                .build();
    }
}
