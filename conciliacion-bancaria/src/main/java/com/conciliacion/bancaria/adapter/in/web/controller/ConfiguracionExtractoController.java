package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.ConfiguracionExtractoRequest;
import com.conciliacion.bancaria.adapter.in.web.dto.ConfiguracionExtractoResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.MovimientoPreviewDTO;
import com.conciliacion.bancaria.adapter.in.web.dto.PruebaConfiguracionResponse;
import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoPruebaConfiguracion;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.port.in.ConfiguracionExtractoUseCase;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoCodec;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/configuraciones-extracto")
@RequiredArgsConstructor
public class ConfiguracionExtractoController {

    private final ConfiguracionExtractoUseCase configuracionExtractoUseCase;
    private final ConfiguracionExtractoCodec configuracionExtractoCodec;

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

    /**
     * Prueba una configuración (aún no guardada) contra un archivo de muestra: parsea
     * con el motor genérico y valida el cuadre, sin persistir nada. Permite al wizard
     * mostrar un preview de movimientos + resultado de cuadre antes de guardar.
     */
    @PostMapping(value = "/probar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<PruebaConfiguracionResponse>> probar(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam("configuracionDetalle") String configuracionDetalleJson,
            @RequestParam(value = "periodo", required = false) String periodo) throws IOException {

        ConfiguracionExtractoDetalle detalle = configuracionExtractoCodec.leer(configuracionDetalleJson);

        if (detalle.getTipoOrigen() == null) {
            throw new CsvValidationException("Complete la configuración antes de probarla con un archivo de muestra.");
        }

        ResultadoPruebaConfiguracion resultado =
                configuracionExtractoUseCase.probarConfiguracion(archivo.getBytes(), detalle, periodo);

        return ResponseEntity.ok(ApiResponse.ok(toPruebaResponse(resultado)));
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ConfiguracionExtracto toDomain(ConfiguracionExtractoRequest req) {
        String detalleJson = req.getConfiguracionDetalle() != null
                ? configuracionExtractoCodec.escribir(req.getConfiguracionDetalle())
                : null;
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

    private PruebaConfiguracionResponse toPruebaResponse(ResultadoPruebaConfiguracion resultado) {
        List<MovimientoPreviewDTO> movimientos = resultado.getMovimientos().stream()
                .map(this::toPreview)
                .toList();
        return PruebaConfiguracionResponse.builder()
                .movimientos(movimientos)
                .totalMovimientos(resultado.getTotalMovimientos())
                .cuadre(resultado.getCuadre())
                .advertencias(resultado.getAdvertencias())
                .build();
    }

    private MovimientoPreviewDTO toPreview(Movimiento m) {
        return MovimientoPreviewDTO.builder()
                .fecha(m.getFecha())
                .descripcion(m.getDescripcion())
                .monto(m.getMonto())
                .tipo(m.getTipo())
                .build();
    }
}
