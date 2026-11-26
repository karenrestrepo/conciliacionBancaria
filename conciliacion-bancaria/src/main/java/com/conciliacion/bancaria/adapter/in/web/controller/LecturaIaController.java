package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.ResultadoLectura;
import com.conciliacion.bancaria.domain.port.in.LecturaIaUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/conciliaciones/{id}/extracto-ia")
@RequiredArgsConstructor
public class LecturaIaController {

    private final LecturaIaUseCase lecturaIaUseCase;

    @PostMapping("/procesar")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ResultadoLectura>> procesar(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    lecturaIaUseCase.procesar(id, archivo.getBytes())));
        } catch (IOException e) {
            throw new CsvValidationException("No se pudo leer el archivo subido");
        }
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<String>> confirmar(
            @PathVariable Long id,
            @RequestBody LecturaExtracto lectura,
            @RequestParam(value = "aceptaSinCuadre", defaultValue = "false") boolean aceptaSinCuadre) {
        String jobId = lecturaIaUseCase.confirmar(id, lectura, aceptaSinCuadre);
        return ResponseEntity.accepted().body(ApiResponse.ok("Motor iniciado", jobId));
    }
}
