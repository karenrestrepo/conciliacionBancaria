package com.conciliacion.bancaria.adapter.in.web.controller;

import com.conciliacion.bancaria.adapter.in.web.dto.ApiResponse;
import com.conciliacion.bancaria.adapter.in.web.dto.MetricasResumenResponse;
import com.conciliacion.bancaria.adapter.out.persistence.repository.*;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import com.conciliacion.bancaria.shared.EstadoSugerencia;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api/v1/metricas")
@RequiredArgsConstructor
public class MetricasController {

    private final ConciliacionJpaRepository conciliacionRepo;
    private final MovimientoBancarioJpaRepository bancarioRepo;
    private final MovimientoContableJpaRepository contableRepo;
    private final SugerenciaJpaRepository sugerenciaRepo;

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('CONTADOR','FINANZAS','ADMIN')")
    public ResponseEntity<ApiResponse<MetricasResumenResponse>> resumen() {

        long totalConciliaciones = conciliacionRepo.count();
        long enBorrador = conciliacionRepo.countByEstado(EstadoConciliacion.BORRADOR);
        long enRevision = conciliacionRepo.countByEstado(EstadoConciliacion.EN_REVISION);
        long cerradas = conciliacionRepo.countByEstado(EstadoConciliacion.CERRADA);
        long totalBancarios = bancarioRepo.count();
        long totalContables = contableRepo.count();
        long totalSugerencias = sugerenciaRepo.count();
        long aceptadas = sugerenciaRepo.countByEstado(EstadoSugerencia.ACEPTADA);
        long rechazadas = sugerenciaRepo.countByEstado(EstadoSugerencia.RECHAZADA);
        long pendientes = sugerenciaRepo.countByEstado(EstadoSugerencia.PENDIENTE_REVISION);

        BigDecimal diferenciaPromedio = conciliacionRepo.findAll().stream()
                .filter(c -> c.getDiferenciaSaldo() != null)
                .map(c -> c.getDiferenciaSaldo().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (cerradas > 0) {
            diferenciaPromedio = diferenciaPromedio.divide(
                    BigDecimal.valueOf(cerradas), 2, RoundingMode.HALF_UP);
        }

        return ResponseEntity.ok(ApiResponse.ok(MetricasResumenResponse.builder()
                .totalConciliaciones(totalConciliaciones)
                .enBorrador(enBorrador)
                .enRevision(enRevision)
                .cerradas(cerradas)
                .totalMovimientosBancarios(totalBancarios)
                .totalMovimientosContables(totalContables)
                .totalSugerencias(totalSugerencias)
                .sugerenciasAceptadas(aceptadas)
                .sugerenciasRechazadas(rechazadas)
                .sugerenciasPendientes(pendientes)
                .diferenciaPromedio(diferenciaPromedio)
                .build()));
    }
}