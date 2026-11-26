package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.AuxiliarLeido;
import com.conciliacion.bancaria.domain.model.MapeoAuxiliar;
import com.conciliacion.bancaria.domain.model.ResultadoAuxiliar;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;
import com.conciliacion.bancaria.domain.port.in.LecturaAuxiliarIaUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/conciliaciones/{id}/auxiliar-ia")
@RequiredArgsConstructor
public class AuxiliarIaController {

    private final LecturaAuxiliarIaUseCase lecturaAuxiliarIaUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping("/procesar")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ResultadoAuxiliar>> procesar(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(ApiResponse.ok(
                lecturaAuxiliarIaUseCase.procesar(id, contenido(archivo))));
    }

    /** Se envía lo que la IA ya interpretó (mapeo o lectura): no se la vuelve a llamar. */
    @PostMapping("/confirmar")
    @PreAuthorize("hasAnyRole('AUXILIAR','CONTADOR','ADMIN')")
    public ResponseEntity<ApiResponse<ResumenCargaAuxiliar>> confirmar(
            @PathVariable Long id,
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "mapeo", required = false) String mapeo,
            @RequestParam(value = "lectura", required = false) String lectura) {
        return ResponseEntity.ok(ApiResponse.ok("Libro auxiliar cargado",
                lecturaAuxiliarIaUseCase.confirmar(id, contenido(archivo),
                        convertir(mapeo, MapeoAuxiliar.class), convertir(lectura, AuxiliarLeido.class))));
    }

    private <T> T convertir(String json, Class<T> tipo) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            throw new CsvValidationException("Los datos de confirmación no son válidos. Suba el archivo de nuevo");
        }
    }

    private byte[] contenido(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new CsvValidationException("No se pudo leer el archivo subido");
        }
    }
}
