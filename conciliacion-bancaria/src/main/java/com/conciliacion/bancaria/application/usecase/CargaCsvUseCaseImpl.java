package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.out.BankStatementParserPort;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoCodec;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.EventLogPort;
import com.conciliacion.bancaria.domain.port.out.JobRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.service.ConciliationEngine;
import com.conciliacion.bancaria.domain.service.CsvValidatorService;
import com.conciliacion.bancaria.domain.service.SiesaXlsParserService;
import com.conciliacion.bancaria.domain.port.out.SugerenciaRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CargaCsvUseCaseImpl implements CargaCsvUseCase {

    private final CsvValidatorService csvValidator;
    private final SiesaXlsParserService siesaParser;
    private final BankStatementParserPort bankStatementParserPort;
    private final ConfiguracionExtractoCodec configuracionExtractoCodec;
    private final ConciliationEngine conciliationEngine;
    private final MovimientoRepositoryPort movimientoRepo;
    private final SugerenciaRepositoryPort sugerenciaRepo;
    private final PartidaRepositoryPort partidaRepo;
    private final JobRepositoryPort jobRepo;
    private final EventLogPort eventLog;
    private final ConciliacionRepositoryPort conciliacionRepo;
    private final ConfiguracionGastoBancarioRepositoryPort gastoRepo;
    private final ConfiguracionExtractoRepositoryPort configuracionExtractoRepo;

    // Mapeo estándar — columnas genéricas. Solo usado por cargarLibroAuxiliar (auxiliar
    // contable), que no pasa por ConfiguracionExtracto ni por el motor de extractos bancarios.
    private static final Map<String, String> MAPEO_ESTANDAR = Map.of(
            "fecha", "fecha",
            "descripcion", "descripcion",
            "monto", "monto",
            "tipo_movimiento", "tipo_movimiento"
    );

    @Override
    @Transactional
    public String cargarExtractoBancario(Long idConciliacion,
                                         MultipartFile archivo,
                                         String nombreBanco) {
        try {
            eventLog.csvUpload(idConciliacion, null, archivo.getOriginalFilename());
            byte[] contenido = archivo.getBytes();

            List<Movimiento> bancarios = parsearExtractoBancario(idConciliacion, contenido);

            // Tarjetas de crédito con auxiliar conjunto: múltiples extractos se acumulan.
            // Cuentas normales: el extracto reemplaza al anterior.
            boolean esAuxiliarConjunto = conciliacionRepo.esAuxiliarConjunto(idConciliacion);
            if (!esAuxiliarConjunto) {
                movimientoRepo.eliminarBancariosNoConciliadosPorConciliacion(idConciliacion);
                sugerenciaRepo.eliminarPendientesPorConciliacion(idConciliacion);
                partidaRepo.eliminarPendientesPorConciliacion(idConciliacion);
            }

            // Agrupar gastos bancarios configurados antes de persistir
            List<Movimiento> bancariosConAgrupacion =
                    aplicarAgrupacionGastos(idConciliacion, bancarios);

            movimientoRepo.guardarBancarios(bancariosConAgrupacion, idConciliacion);

            // Lanzar motor asíncrono y retornar job_id (TRD RT-03)
            String jobId = jobRepo.crearJob(idConciliacion);
            ejecutarMotorAsync(jobId, idConciliacion);
            return jobId;

        } catch (CsvValidationException e) {
            eventLog.csvValidationFailed(idConciliacion, e.getMessage());
            throw e;
        } catch (Exception e) {
            eventLog.csvValidationFailed(idConciliacion, e.getMessage());
            throw new CsvValidationException("Error procesando el archivo: "
                    + e.getMessage());
        }
    }

    /**
     * Busca la ConfiguracionExtracto activa para la conciliación: la primera configuración
     * activa del banco que aplique a todas las cuentas o incluya la cuenta de la conciliación.
     */
    private java.util.Optional<ConfiguracionExtracto> buscarConfiguracionActiva(Long idConciliacion) {
        Conciliacion conciliacion = conciliacionRepo.buscarPorId(idConciliacion).orElse(null);
        if (conciliacion == null || conciliacion.getIdBanco() == null) {
            return java.util.Optional.empty();
        }

        Long idBanco  = conciliacion.getIdBanco();
        Long idCuenta = conciliacion.getIdCuenta();

        return configuracionExtractoRepo.listarPorBanco(idBanco).stream()
                .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                .filter(c -> c.isAplicaParaTodasLasCuentas()
                        || (c.getIdsCuentas() != null && c.getIdsCuentas().contains(idCuenta)))
                .findFirst();
    }

    /**
     * Único camino de parseo de extractos bancarios: resuelve la configuración activa
     * del banco/cuenta y delega al motor genérico (BankStatementParserPort), que despacha
     * internamente por el tipoOrigen declarado en la propia configuración. Sin ramas por
     * tipo de archivo ni por nombre de banco.
     */
    private List<Movimiento> parsearExtractoBancario(Long idConciliacion, byte[] contenido) {
        Conciliacion conciliacion = conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new CsvValidationException(
                        "No se encontró la conciliación " + idConciliacion));

        ConfiguracionExtracto config = buscarConfiguracionActiva(idConciliacion)
                .orElseThrow(() -> new CsvValidationException(
                        "No se encontró configuración de extracto activa para este banco/cuenta. "
                        + "Configure el extracto en Bancos → Configuración de extractos."));

        ConfiguracionExtractoDetalle detalle = configuracionExtractoCodec.leer(config.getConfiguracionDetalle());
        String periodo = conciliacion.getPeriodo();

        log.info("Parseando extracto con configuración '{}' (id={}, tipoOrigen={}), periodo={}",
                config.getNombre(), config.getId(), detalle.getTipoOrigen(), periodo);

        return bankStatementParserPort.parsear(contenido, detalle, periodo);
    }

    @Override
    @Transactional
    public String cargarLibroAuxiliar(Long idConciliacion, MultipartFile archivo) {
        try {
            eventLog.csvUpload(idConciliacion, null, archivo.getOriginalFilename());
            byte[] contenido = archivo.getBytes();

            List<Movimiento> delArchivo = esArchivoXls(archivo.getOriginalFilename())
                    ? siesaParser.parsear(contenido)
                    : csvValidator.parsear(contenido, MAPEO_ESTANDAR);

            // Huellas de TODOS los contables existentes (incluyendo CONCILIADO) para que
            // los movimientos ya conciliados no vuelvan a insertarse como "nuevos".
            Set<String> huellas = movimientoRepo.buscarTodosContablesPorConciliacion(idConciliacion)
                    .stream()
                    .map(this::huella)
                    .collect(Collectors.toSet());

            List<Movimiento> soloNuevos = delArchivo.stream()
                    .filter(m -> !huellas.contains(huella(m)))
                    .toList();

            if (soloNuevos.isEmpty()) {
                log.info("Re-carga de auxiliar sin movimientos nuevos (conciliacion={})", idConciliacion);
                return "sin-cambios";
            }

            log.info("Re-carga incremental de auxiliar: {} nuevos de {} en archivo (conciliacion={})",
                    soloNuevos.size(), delArchivo.size(), idConciliacion);

            List<Movimiento> persistidos = movimientoRepo.guardarContables(soloNuevos, idConciliacion);

            String jobId = jobRepo.crearJob(idConciliacion);
            ejecutarMotorIncrementalAsync(jobId, idConciliacion, persistidos);
            return jobId;
        } catch (CsvValidationException e) {
            eventLog.csvValidationFailed(idConciliacion, e.getMessage());
            throw e;
        } catch (Exception e) {
            eventLog.csvValidationFailed(idConciliacion, e.getMessage());
            throw new CsvValidationException("Error procesando el archivo: "
                    + e.getMessage());
        }
    }

    private String huella(Movimiento m) {
        return m.getFecha()
                + "|" + m.getMonto().stripTrailingZeros().toPlainString()
                + "|" + m.getTipo()
                + "|" + (m.getDescripcion() != null ? m.getDescripcion().trim().toLowerCase() : "");
    }

    /**
     * Identifica movimientos del extracto cuya descripción coincide con algún patrón
     * de gasto bancario configurado para la cuenta. Los agrupa sumando sus montos por
     * tipo (DEBITO/CREDITO) y crea movimientos sintéticos que representan el total,
     * de modo que coincidan con la nota única que aparece en el auxiliar contable.
     * Los movimientos originales se marcan como AGRUPADO y no participan en el motor.
     */
    private List<Movimiento> aplicarAgrupacionGastos(Long idConciliacion,
                                                      List<Movimiento> bancarios) {
        Long idCuenta = conciliacionRepo.obtenerIdCuentaPorConciliacion(idConciliacion);
        if (idCuenta == null) return bancarios;

        List<String> patrones = gastoRepo.listarDescripcionesActivas(idCuenta);
        if (patrones.isEmpty()) return bancarios;

        List<Movimiento> gastos = new ArrayList<>();
        List<Movimiento> normales = new ArrayList<>();

        for (Movimiento m : bancarios) {
            boolean coincide = patrones.stream().anyMatch(patron ->
                    m.getDescripcion() != null &&
                    m.getDescripcion().toLowerCase().contains(patron.toLowerCase()));
            if (coincide) {
                gastos.add(m.withEstado(EstadoMovimiento.AGRUPADO));
            } else {
                normales.add(m);
            }
        }

        if (gastos.isEmpty()) return bancarios;

        // Fecha del movimiento agrupado = última fecha encontrada entre los gastos
        LocalDate fechaResumen = gastos.stream()
                .map(Movimiento::getFecha)
                .filter(f -> f != null)
                .max(Comparator.naturalOrder())
                .orElse(LocalDate.now());

        // Agrupar por tipo y crear un movimiento sintético por cada tipo distinto
        Map<String, BigDecimal> totalPorTipo = gastos.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getTipo() != null ? m.getTipo() : "DEBITO",
                        Collectors.reducing(BigDecimal.ZERO, Movimiento::getMonto, BigDecimal::add)));

        List<Movimiento> sinteticos = totalPorTipo.entrySet().stream()
                .map(e -> Movimiento.builder()
                        .fecha(fechaResumen)
                        .descripcion("GASTOS BANCARIOS AGRUPADOS")
                        .monto(e.getValue())
                        .tipo(e.getKey())
                        .estado(EstadoMovimiento.PENDIENTE)
                        .build())
                .toList();

        log.info("Agrupación de gastos bancarios: {} movimientos agrupados → {} movimientos sintéticos (conciliacion={})",
                gastos.size(), sinteticos.size(), idConciliacion);

        List<Movimiento> resultado = new ArrayList<>(normales);
        resultado.addAll(sinteticos);
        resultado.addAll(gastos); // persist AGRUPADO ones for traceability
        return resultado;
    }

    @Override
    @Transactional
    public String reprocesarMotor(Long idConciliacion) {
        limpiarContablesDuplicados(idConciliacion);
        sugerenciaRepo.eliminarPendientesPorConciliacion(idConciliacion);
        partidaRepo.eliminarPendientesPorConciliacion(idConciliacion);
        movimientoRepo.resetEstadosBancariosSugeridos(idConciliacion);
        movimientoRepo.resetEstadosContablesSugeridos(idConciliacion);
        String jobId = jobRepo.crearJob(idConciliacion);
        ejecutarMotorAsync(jobId, idConciliacion);
        return jobId;
    }

    private void limpiarContablesDuplicados(Long idConciliacion) {
        List<Movimiento> todos = movimientoRepo.buscarTodosContablesPorConciliacion(idConciliacion);

        Set<String> huellasConciliados = todos.stream()
                .filter(m -> m.getEstado() == EstadoMovimiento.CONCILIADO)
                .map(this::huella)
                .collect(Collectors.toSet());

        if (huellasConciliados.isEmpty()) return;

        List<Long> duplicados = todos.stream()
                .filter(m -> m.getEstado() != EstadoMovimiento.CONCILIADO)
                .filter(m -> huellasConciliados.contains(huella(m)))
                .map(Movimiento::getId)
                .toList();

        if (!duplicados.isEmpty()) {
            log.info("Eliminando {} contables duplicados de movimientos ya conciliados (conciliacion={})",
                    duplicados.size(), idConciliacion);
            movimientoRepo.eliminarContablesPorIds(duplicados);
        }
    }

    private boolean esArchivoXls(String nombre) {
        if (nombre == null) return false;
        String lower = nombre.toLowerCase();
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    // Motor incremental — sólo compara nuevos contables contra bancarios sin sugerencia activa
    @Async("conciliacionExecutor")
    public void ejecutarMotorIncrementalAsync(String jobId, Long idConciliacion,
                                              List<Movimiento> nuevosContables) {
        long inicio = System.currentTimeMillis();
        try {
            jobRepo.actualizarProgreso(jobId, 10);

            // Bancarios disponibles: PENDIENTE y sin sugerencia activa
            List<Movimiento> bancariosPendientes =
                    movimientoRepo.buscarBancariosPendientesPorConciliacion(idConciliacion);
            Set<Long> yaEmparejados =
                    sugerenciaRepo.buscarBancarioIdsConSugerenciaPendiente(idConciliacion);
            List<Movimiento> bancariosLibres = bancariosPendientes.stream()
                    .filter(b -> !yaEmparejados.contains(b.getId()))
                    .toList();

            jobRepo.actualizarProgreso(jobId, 30);

            if (bancariosLibres.isEmpty()) {
                // Sin bancarios libres: los nuevos contables van directo a partidas
                partidaRepo.guardarTodas(
                        nuevosContables.stream()
                                .map(c -> com.conciliacion.bancaria.domain.model.PartidaConciliatoria.builder()
                                        .idConciliacion(idConciliacion)
                                        .idMovimiento(c.getId())
                                        .tipoOrigen("CONTABLE")
                                        .estado("PENDIENTE")
                                        .build())
                                .toList());
                jobRepo.completar(jobId);
                return;
            }

            ConciliationEngine.ResultadoMotor resultado =
                    conciliationEngine.ejecutar(idConciliacion, bancariosLibres, nuevosContables);
            jobRepo.actualizarProgreso(jobId, 75);

            sugerenciaRepo.guardarTodas(resultado.sugerencias());

            // Los bancarios que obtuvieron sugerencia ya no son pendientes; eliminar su partida
            resultado.sugerencias().stream()
                    .map(s -> s.getMovimientoBancario().getId())
                    .distinct()
                    .forEach(id -> partidaRepo.eliminarPendientePorMovimiento(id, "BANCARIO"));

            // Guardar sólo partidas de contables nuevos que no encontraron par
            partidaRepo.guardarTodas(resultado.partidasContables());

            jobRepo.actualizarProgreso(jobId, 90);

            long duracion = System.currentTimeMillis() - inicio;
            eventLog.engineCompleted(idConciliacion, duracion,
                    bancariosLibres.size() + nuevosContables.size());

            jobRepo.completar(jobId);
        } catch (Exception e) {
            log.error("Error en motor incremental job={}: {}", jobId, e.getMessage());
            eventLog.integrationError(idConciliacion, e.getMessage());
            jobRepo.fallar(jobId, e.getMessage());
        }
    }

    // Motor asíncrono — se ejecuta en el pool "conciliacionExecutor"
    @Async("conciliacionExecutor")
    public void ejecutarMotorAsync(String jobId, Long idConciliacion) {
        long inicio = System.currentTimeMillis();
        try {
            jobRepo.actualizarProgreso(jobId, 10);

            List<Movimiento> bancarios =
                    movimientoRepo.buscarBancariosPorConciliacion(idConciliacion);
            jobRepo.actualizarProgreso(jobId, 30);

            List<Movimiento> contables =
                    movimientoRepo.buscarContablesPorConciliacion(idConciliacion);
            jobRepo.actualizarProgreso(jobId, 50);

            ConciliationEngine.ResultadoMotor resultado =
                    conciliationEngine.ejecutar(idConciliacion, bancarios, contables);
            jobRepo.actualizarProgreso(jobId, 75);

            sugerenciaRepo.guardarTodas(resultado.sugerencias());
            partidaRepo.guardarTodas(resultado.partidasBancarias());
            partidaRepo.guardarTodas(resultado.partidasContables());
            jobRepo.actualizarProgreso(jobId, 90);

            long duracion = System.currentTimeMillis() - inicio;
            eventLog.engineCompleted(idConciliacion, duracion,
                    bancarios.size() + contables.size());

            jobRepo.completar(jobId);

            // Si ambos archivos están cargados y la conciliación sigue en BORRADOR,
            // avanzar automáticamente a EN_REVISION.
            if (!bancarios.isEmpty() && !contables.isEmpty()) {
                conciliacionRepo.buscarPorId(idConciliacion).ifPresent(c -> {
                    try {
                        conciliacionRepo.guardar(c.pasarAEnRevision());
                        log.info("Conciliación {} avanzada a EN_REVISION automáticamente", idConciliacion);
                    } catch (Exception ex) {
                        log.debug("Conciliación {} ya no está en BORRADOR ({})", idConciliacion, ex.getMessage());
                    }
                });
            }

        } catch (Exception e) {
            log.error("Error en motor de conciliación job={}: {}", jobId, e.getMessage());
            eventLog.integrationError(idConciliacion, e.getMessage());
            jobRepo.fallar(jobId, e.getMessage());
        }
    }
}
