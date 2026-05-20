package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.Sugerencia;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import com.conciliacion.bancaria.shared.EstadoSugerencia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class ConciliationEngine {

    // Ventana de proximidad de fecha (TRD RT-03): ±3 días
    private static final long VENTANA_DIAS = 3;

    // Umbral mínimo de confianza para generar sugerencia
    private static final BigDecimal CONFIANZA_MINIMA = new BigDecimal("0.50");

    public record ResultadoMotor(
            List<Sugerencia> sugerencias,
            List<PartidaConciliatoria> partidasBancarias,
            List<PartidaConciliatoria> partidasContables
    ) {}

    // ── Algoritmo principal (8 pasos del PRD) ────────────────────────────────

    public ResultadoMotor ejecutar(Long idConciliacion,
                                   List<Movimiento> bancarios,
                                   List<Movimiento> contables) {

        // Paso 1: copias mutables para marcar conciliados
        List<Movimiento> bancariosPendientes = new ArrayList<>(bancarios);
        List<Movimiento> contablesPendientes = new ArrayList<>(contables);
        List<Sugerencia> sugerencias = new ArrayList<>();

        // Paso 2: monto exacto + mismo tipo (confianza 1.0)
        sugerencias.addAll(
                emparejarPorMontoExacto(idConciliacion,
                        bancariosPendientes, contablesPendientes)
        );

        // Paso 3: monto exacto + fecha próxima ±3 días (confianza 0.85)
        eliminarConciliados(bancariosPendientes);
        eliminarConciliados(contablesPendientes);
        sugerencias.addAll(
                emparejarPorMontoFechaProxima(idConciliacion,
                        bancariosPendientes, contablesPendientes)
        );

        // Paso 4: monto aproximado ±0.01 (diferencias de redondeo, confianza 0.70)
        eliminarConciliados(bancariosPendientes);
        eliminarConciliados(contablesPendientes);
        sugerencias.addAll(
                emparejarPorMontoAproximado(idConciliacion,
                        bancariosPendientes, contablesPendientes)
        );

        // Paso 5: resolver ambigüedades — si un contable tiene múltiples bancarios
        // con mismo monto, gana el de mayor frecuencia histórica (criterio PRD §5.3)
        // En V1 académico: gana el de fecha más próxima como criterio secundario
        resolverAmbiguedades(sugerencias);

        // Paso 6: eliminar conciliados finales
        eliminarConciliados(bancariosPendientes);
        eliminarConciliados(contablesPendientes);

        // Paso 7: movimientos sin par → partidas conciliatorias
        List<PartidaConciliatoria> partidasBancarias =
                generarPartidas(idConciliacion, bancariosPendientes, "BANCARIO");
        List<PartidaConciliatoria> partidasContables =
                generarPartidas(idConciliacion, contablesPendientes, "CONTABLE");

        // Paso 8: retornar resultado completo
        return new ResultadoMotor(sugerencias, partidasBancarias, partidasContables);
    }

    // ── Paso 2: monto exacto + mismo tipo ────────────────────────────────────

    private List<Sugerencia> emparejarPorMontoExacto(Long idConciliacion,
                                                     List<Movimiento> bancarios,
                                                     List<Movimiento> contables) {
        List<Sugerencia> resultado = new ArrayList<>();

        for (Movimiento bancario : bancarios) {
            if (bancario.getEstado() == EstadoMovimiento.SUGERIDO) continue;

            // Preferir el contable con fecha más cercana cuando hay varios con mismo monto
            Optional<Movimiento> contable = contables.stream()
                    .filter(c -> c.getEstado() != EstadoMovimiento.SUGERIDO)
                    .filter(c -> c.getTipo().equals(bancario.getTipo()))
                    .filter(c -> c.getMonto().compareTo(bancario.getMonto()) == 0)
                    .min(Comparator.comparingLong(c ->
                            Math.abs(ChronoUnit.DAYS.between(bancario.getFecha(), c.getFecha()))));

            contable.ifPresent(c -> {
                resultado.add(construirSugerencia(idConciliacion, bancario, c,
                        new BigDecimal("1.00"), "MONTO_EXACTO"));
                marcarSugerido(bancarios, bancario);
                marcarSugerido(contables, c);
            });
        }
        return resultado;
    }

    // ── Paso 3: monto exacto + fecha próxima ─────────────────────────────────

    private List<Sugerencia> emparejarPorMontoFechaProxima(Long idConciliacion,
                                                           List<Movimiento> bancarios,
                                                           List<Movimiento> contables) {
        List<Sugerencia> resultado = new ArrayList<>();

        for (Movimiento bancario : bancarios) {
            if (bancario.getEstado() == EstadoMovimiento.SUGERIDO) continue;

            Optional<Movimiento> contable = contables.stream()
                    .filter(c -> c.getEstado() != EstadoMovimiento.SUGERIDO)
                    .filter(c -> c.getTipo().equals(bancario.getTipo()))
                    .filter(c -> c.getMonto().compareTo(bancario.getMonto()) == 0)
                    .filter(c -> Math.abs(ChronoUnit.DAYS.between(
                            bancario.getFecha(), c.getFecha())) <= VENTANA_DIAS)
                    .min(Comparator.comparingLong(c ->
                            Math.abs(ChronoUnit.DAYS.between(bancario.getFecha(), c.getFecha()))))
                    .stream().findFirst();

            contable.ifPresent(c -> {
                resultado.add(construirSugerencia(idConciliacion, bancario, c,
                        new BigDecimal("0.85"), "MONTO_FECHA_PROXIMA"));
                marcarSugerido(bancarios, bancario);
                marcarSugerido(contables, c);
            });
        }
        return resultado;
    }

    // ── Paso 4: monto aproximado ±0.01 ───────────────────────────────────────

    private List<Sugerencia> emparejarPorMontoAproximado(Long idConciliacion,
                                                         List<Movimiento> bancarios,
                                                         List<Movimiento> contables) {
        List<Sugerencia> resultado = new ArrayList<>();
        BigDecimal tolerancia = new BigDecimal("0.01");

        for (Movimiento bancario : bancarios) {
            if (bancario.getEstado() == EstadoMovimiento.SUGERIDO) continue;

            Optional<Movimiento> contable = contables.stream()
                    .filter(c -> c.getEstado() != EstadoMovimiento.SUGERIDO)
                    .filter(c -> c.getTipo().equals(bancario.getTipo()))
                    .filter(c -> bancario.getMonto().subtract(c.getMonto())
                            .abs().compareTo(tolerancia) <= 0)
                    .findFirst();

            contable.ifPresent(c -> {
                resultado.add(construirSugerencia(idConciliacion, bancario, c,
                        new BigDecimal("0.70"), "MONTO_APROXIMADO"));
                marcarSugerido(bancarios, bancario);
                marcarSugerido(contables, c);
            });
        }
        return resultado;
    }

    // ── Paso 5: resolver ambigüedades ─────────────────────────────────────────

    private void resolverAmbiguedades(List<Sugerencia> sugerencias) {
        // Detectar contables duplicados (mismo id asignado a dos bancarios)
        Map<Long, List<Sugerencia>> porContable = sugerencias.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getMovimientoContable().getId()));

        porContable.forEach((idContable, lista) -> {
            if (lista.size() > 1) {
                // Criterio secundario: menor diferencia de fechas gana
                lista.sort(Comparator.comparingLong(s ->
                        Math.abs(ChronoUnit.DAYS.between(
                                s.getMovimientoBancario().getFecha(),
                                s.getMovimientoContable().getFecha()))));

                // La primera queda, las demás se marcan REASIGNADA
                for (int i = 1; i < lista.size(); i++) {
                    Sugerencia reasignada = lista.get(i).reasignar();
                    lista.set(i, reasignada);
                }
            }
        });
    }

    // ── Paso 7: generar partidas para movimientos sin par ─────────────────────

    private List<PartidaConciliatoria> generarPartidas(Long idConciliacion,
                                                       List<Movimiento> pendientes,
                                                       String tipoOrigen) {
        return pendientes.stream()
                .filter(m -> m.getEstado() == EstadoMovimiento.PENDIENTE)
                .map(m -> PartidaConciliatoria.builder()
                        .idConciliacion(idConciliacion)
                        .idMovimiento(m.getId())
                        .tipoOrigen(tipoOrigen)
                        .estado("PENDIENTE")
                        .build())
                .collect(Collectors.toList());
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private Sugerencia construirSugerencia(Long idConciliacion,
                                           Movimiento bancario,
                                           Movimiento contable,
                                           BigDecimal confianza,
                                           String criterio) {
        return Sugerencia.builder()
                .idConciliacion(idConciliacion)
                .movimientoBancario(bancario)
                .movimientoContable(contable)
                .confianza(confianza)
                .criterio(criterio)
                .estado(EstadoSugerencia.PENDIENTE_REVISION)
                .build();
    }

    private void marcarSugerido(List<Movimiento> lista, Movimiento objetivo) {
        int idx = lista.indexOf(objetivo);
        if (idx >= 0) {
            lista.set(idx, objetivo.marcarSugerido());
        }
    }

    private void eliminarConciliados(List<Movimiento> lista) {
        lista.removeIf(m -> m.getEstado() == EstadoMovimiento.CONCILIADO);
    }
}