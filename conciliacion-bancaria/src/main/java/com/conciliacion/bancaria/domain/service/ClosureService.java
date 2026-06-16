package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.ConciliacionCerradaException;
import com.conciliacion.bancaria.domain.exception.InvalidPeriodTransferException;
import com.conciliacion.bancaria.domain.exception.PartidaPendienteException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.PartidaConciliatoria;
import com.conciliacion.bancaria.domain.model.ConfiguracionGastoBancario;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoConciliacion;
import com.conciliacion.bancaria.shared.EstadoMovimiento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ClosureService {

    private final ConciliacionRepositoryPort conciliacionRepo;
    private final PartidaRepositoryPort partidaRepo;
    private final MovimientoRepositoryPort movimientoRepo;
    private final ConfiguracionGastoBancarioRepositoryPort gastoRepo;

    public ClosureService(ConciliacionRepositoryPort conciliacionRepo,
                          PartidaRepositoryPort partidaRepo,
                          MovimientoRepositoryPort movimientoRepo,
                          ConfiguracionGastoBancarioRepositoryPort gastoRepo) {
        this.conciliacionRepo = conciliacionRepo;
        this.partidaRepo = partidaRepo;
        this.movimientoRepo = movimientoRepo;
        this.gastoRepo = gastoRepo;
    }

    // ── Cierre de conciliación (TRD RT-05) ───────────────────────────────────

    public Conciliacion cerrar(Long idConciliacion, Long idUsuarioContador,
                               BigDecimal saldoExtracto, BigDecimal saldoAuxiliar) {

        Conciliacion conciliacion = conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conciliación no encontrada: " + idConciliacion));

        if (conciliacion.esCerrada()) {
            throw new ConciliacionCerradaException(idConciliacion);
        }

        // Validar que no haya partidas pendientes sin justificar
        List<PartidaConciliatoria> pendientes =
                partidaRepo.buscarPendientesSinJustificar(idConciliacion);

        if (!pendientes.isEmpty()) {
            throw new PartidaPendienteException(pendientes.size());
        }

        // Ejecutar transición de estado en el dominio
        Conciliacion cerrada = conciliacion.cerrar(idUsuarioContador,
                saldoExtracto, saldoAuxiliar);

        return conciliacionRepo.guardar(cerrada);
    }

    // ── Arrastre de partidas (TRD RT-05 edge case) ───────────────────────────
    // Sub-caso 1: período destino está ABIERTO → se arrastra normalmente
    // Sub-caso 2: período destino está CERRADO → se lanza excepción
    // Sub-caso 3: período destino no existe → se crea automáticamente al arrastrar

    public PartidaConciliatoria arrastrarPartida(Long idPartida,
                                                 String periodoDestino,
                                                 Long idUsuario) {

        PartidaConciliatoria partida = partidaRepo.buscarPorId(idPartida)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Partida no encontrada: " + idPartida));

        // Verificar estado del período destino
        EstadoConciliacion estadoDestino = conciliacionRepo
                .buscarPorPeriodo(periodoDestino)
                .map(Conciliacion::getEstado)
                .orElse(null); // null = período no existe aún (sub-caso 3)

        // Sub-caso 2: período destino CERRADO → error
        if (estadoDestino == EstadoConciliacion.CERRADA) {
            throw new InvalidPeriodTransferException(periodoDestino);
        }

        // Sub-caso 1 y 3: arrastrar
        PartidaConciliatoria arrastrada = partida
                .withEstado("ARRASTRADA")
                .withPeriodoArrastre(periodoDestino);

        return partidaRepo.actualizar(arrastrada);
    }

    // ── Cruce manual de partidas ─────────────────────────────────────────────

    /**
     * tipo=GASTO_BANCARIO → marca el origen como AGRUPADO en extracto y guarda la descripción
     *   como ConfiguracionGastoBancario para la cuenta.
     * tipo=CRUZAR → cruza origen con idsDestino; si la diferencia neta es 0 → CRUZADA completa,
     *   si no → CRUZADA con justificación "INCOMPLETO|diferencia:X".
     */
    public List<PartidaConciliatoria> cruzarPartidas(Long idOrigen, List<Long> idsDestino,
                                                     String tipo, Long idConciliacion) {
        PartidaConciliatoria origen = partidaRepo.buscarPorId(idOrigen)
                .orElseThrow(() -> new IllegalArgumentException("Partida no encontrada: " + idOrigen));

        return switch (tipo) {
            case "GASTO_BANCARIO" -> {
                if ("BANCARIO".equals(origen.getTipoOrigen())) {
                    movimientoRepo.actualizarEstadoBancario(origen.getIdMovimiento(), EstadoMovimiento.AGRUPADO);
                    List<Movimiento> movs = movimientoRepo.buscarBancariosPorIds(List.of(origen.getIdMovimiento()));
                    if (!movs.isEmpty()) {
                        Long idCuenta = conciliacionRepo.buscarPorId(idConciliacion)
                                .map(c -> c.getIdCuenta())
                                .orElse(null);
                        String descripcion = movs.get(0).getDescripcion();
                        if (idCuenta != null && descripcion != null && !descripcion.isBlank()) {
                            gastoRepo.guardar(ConfiguracionGastoBancario.builder()
                                    .idCuenta(idCuenta)
                                    .descripcion(descripcion.trim())
                                    .activo(true)
                                    .build());
                        }
                    }
                }
                yield List.of(partidaRepo.actualizar(origen
                        .withEstado("JUSTIFICADA")
                        .withJustificacion("GASTO_BANCARIO")
                        .withFechaJustificacion(LocalDate.now())));
            }

            case "CRUZAR" -> {
                List<PartidaConciliatoria> destinos = idsDestino.stream()
                        .map(id -> partidaRepo.buscarPorId(id)
                                .orElseThrow(() -> new IllegalArgumentException("Partida no encontrada: " + id)))
                        .toList();

                // Suma neta: CREDITO = +monto, DEBITO = -monto
                BigDecimal neto = signedMonto(origen);
                for (PartidaConciliatoria d : destinos) neto = neto.add(signedMonto(d));

                String justificacion = neto.compareTo(BigDecimal.ZERO) == 0
                        ? "Cruzado manualmente"
                        : "INCOMPLETO|diferencia:" + neto.toPlainString();

                List<PartidaConciliatoria> todas = new ArrayList<>();
                todas.add(origen);
                todas.addAll(destinos);

                yield todas.stream().map(p ->
                        partidaRepo.actualizar(p
                                .withEstado("CRUZADA")
                                .withJustificacion(justificacion)
                                .withFechaJustificacion(LocalDate.now()))
                ).toList();
            }

            default -> throw new IllegalArgumentException("Tipo de cruce no válido: " + tipo);
        };
    }

    private BigDecimal signedMonto(PartidaConciliatoria p) {
        if (p.getMontoMovimiento() == null) return BigDecimal.ZERO;
        return "DEBITO".equals(p.getTipoMovimiento())
                ? p.getMontoMovimiento().negate()
                : p.getMontoMovimiento();
    }

    // ── Justificación de partida ──────────────────────────────────────────────

    public PartidaConciliatoria justificar(Long idPartida,
                                           String justificacion,
                                           java.time.LocalDate fecha) {

        PartidaConciliatoria partida = partidaRepo.buscarPorId(idPartida)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Partida no encontrada: " + idPartida));

        PartidaConciliatoria justificada = partida
                .withJustificacion(justificacion)
                .withFechaJustificacion(fecha)
                .withEstado("JUSTIFICADA");

        return partidaRepo.actualizar(justificada);
    }
}