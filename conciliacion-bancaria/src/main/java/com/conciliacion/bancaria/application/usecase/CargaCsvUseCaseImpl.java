package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;
import com.conciliacion.bancaria.domain.model.Sugerencia;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionExtractoRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.ConfiguracionGastoBancarioRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.EventLogPort;
import com.conciliacion.bancaria.domain.port.out.JobRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.service.ConciliationEngine;
import com.conciliacion.bancaria.domain.service.CsvValidatorService;
import com.conciliacion.bancaria.domain.service.ExtractoBancarioTxtAnchoFijoParserService;
import com.conciliacion.bancaria.domain.service.ExtractoBancarioXlsxParserService;
import com.conciliacion.bancaria.domain.service.MovimientoReversionService;
import com.conciliacion.bancaria.domain.service.SiesaXlsParserService;
import com.conciliacion.bancaria.shared.TipoArchivoExtracto;
import com.conciliacion.bancaria.domain.port.out.SugerenciaRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.PartidaRepositoryPort;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CargaCsvUseCaseImpl implements CargaCsvUseCase {

    private final CsvValidatorService csvValidator;
    private final SiesaXlsParserService siesaParser;
    private final ExtractoBancarioXlsxParserService extractoXlsxParser;
    private final ExtractoBancarioTxtAnchoFijoParserService extractoTxtAnchoFijoParser;
    private final ConciliationEngine conciliationEngine;
    private final MovimientoRepositoryPort movimientoRepo;
    private final SugerenciaRepositoryPort sugerenciaRepo;
    private final PartidaRepositoryPort partidaRepo;
    private final JobRepositoryPort jobRepo;
    private final EventLogPort eventLog;
    private final ConciliacionRepositoryPort conciliacionRepo;
    private final ConfiguracionGastoBancarioRepositoryPort gastoRepo;
    private final ConfiguracionExtractoRepositoryPort configuracionExtractoRepo;
    private final MovimientoReversionService reversionService;

    /**
     * Auto-referencia al proxy Spring de este mismo bean. Los métodos {@code @Async}
     * de esta clase se invocaban antes como {@code this.metodo(...)} desde otros
     * métodos de la MISMA clase — el proxy AOP de Spring no intercepta llamadas
     * internas, así que {@code @Async} nunca surtía efecto (corría síncrono, dentro
     * de la misma transacción). Se llama a través de {@code self} en vez de
     * directo para que sí pase por el proxy. No es {@code final} ni participa del
     * constructor (Lombok @RequiredArgsConstructor solo incluye campos {@code final})
     * porque no puede auto-inyectarse durante su propia construcción; @Lazy difiere
     * la resolución al primer uso real. En tests unitarios (fuera de un contenedor
     * Spring) se asigna manualmente a la misma instancia tras construirla.
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    CargaCsvUseCaseImpl self;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mapeo estándar — columnas genéricas
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

            List<Movimiento> bancarios;
            ConfiguracionExtracto configActiva = buscarConfiguracionActiva(idConciliacion).orElse(null);

            if (configActiva != null && configActiva.getTipoArchivo() == TipoArchivoExtracto.TXT
                    && esFormatoTxtAnchoFijo(configActiva)) {
                bancarios = parsearExtractoTxtAnchoFijo(idConciliacion, contenido, configActiva);
            } else if (esArchivoXls(archivo.getOriginalFilename())) {
                bancarios = parsearExtractoXlsx(idConciliacion, contenido);
            } else {
                bancarios = csvValidator.parsear(contenido, MAPEO_ESTANDAR);
            }

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
            self.ejecutarMotorAsync(jobId, idConciliacion);
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

    private boolean esFormatoTxtAnchoFijo(ConfiguracionExtracto config) {
        Map<String, Object> detalle = parseConfigJson(config.getConfiguracionDetalle());
        return "ANCHO_FIJO".equals(String.valueOf(detalle.getOrDefault("formatoTxt", "")));
    }

    /**
     * Busca la ConfiguracionExtracto activa para la conciliación y parsea el XLSX
     * usando esa configuración. Si no se encuentra configuración, lanza excepción.
     */
    private List<Movimiento> parsearExtractoXlsx(Long idConciliacion, byte[] contenido) {
        Conciliacion conciliacion = conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new CsvValidationException(
                        "No se encontró la conciliación " + idConciliacion));

        if (conciliacion.getIdBanco() == null) {
            throw new CsvValidationException(
                    "La conciliación no tiene banco asociado; "
                    + "configure primero la cuenta bancaria.");
        }

        String periodo = conciliacion.getPeriodo();

        ConfiguracionExtracto config = buscarConfiguracionActiva(idConciliacion)
                .orElseThrow(() -> new CsvValidationException(
                        "No se encontró configuración de extracto XLSX activa para este banco/cuenta. "
                        + "Configure el extracto en Bancos → Configuración de extractos."));

        Map<String, Object> detalleMap = parseConfigJson(config.getConfiguracionDetalle());

        log.info("Parseando extracto XLSX con configuración '{}' (id={}), periodo={}",
                config.getNombre(), config.getId(), periodo);

        return extractoXlsxParser.parsear(contenido, detalleMap, periodo);
    }

    /**
     * Parsea un extracto de texto plano de ancho fijo (ej. Davivienda) usando la
     * configuración activa ya resuelta.
     */
    private List<Movimiento> parsearExtractoTxtAnchoFijo(Long idConciliacion, byte[] contenido,
                                                          ConfiguracionExtracto config) {
        Conciliacion conciliacion = conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new CsvValidationException(
                        "No se encontró la conciliación " + idConciliacion));
        String periodo = conciliacion.getPeriodo();

        Map<String, Object> detalleMap = parseConfigJson(config.getConfiguracionDetalle());

        log.info("Parseando extracto TXT ancho fijo con configuración '{}' (id={}), periodo={}",
                config.getNombre(), config.getId(), periodo);

        return extractoTxtAnchoFijoParser.parsear(contenido, detalleMap, periodo);
    }

    private Map<String, Object> parseConfigJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("No se pudo parsear configuracionDetalle JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    @Transactional
    public ResumenCargaAuxiliar cargarLibroAuxiliar(Long idConciliacion, MultipartFile archivo) {
        try {
            eventLog.csvUpload(idConciliacion, null, archivo.getOriginalFilename());
            byte[] contenido = archivo.getBytes();

            List<Movimiento> delArchivo = esArchivoXls(archivo.getOriginalFilename())
                    ? siesaParser.parsear(contenido)
                    : csvValidator.parsear(contenido, MAPEO_ESTANDAR);

            return procesarRecargaAuxiliar(idConciliacion, delArchivo);
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
     * Diff + reversión + persistencia de una recarga del auxiliar, separado del parseo
     * del archivo para poder testearse con listas de {@link Movimiento} construidas a
     * mano (ver {@code CargaCsvUseCaseImplTest}).
     *
     * Compara por multiconjunto (cuenta ocurrencias de cada huella en vez de solo
     * verificar membresía) para no descartar en silencio duplicados legítimos —dos
     * movimientos reales con la misma huella— como si fueran el mismo renglón.
     */
    ResumenCargaAuxiliar procesarRecargaAuxiliar(Long idConciliacion, List<Movimiento> delArchivo) {
        List<Movimiento> todosContables = movimientoRepo.buscarTodosContablesPorConciliacion(idConciliacion);

        Map<String, List<Movimiento>> existentesPorHuella = todosContables.stream()
                .collect(Collectors.groupingBy(this::huella));
        Map<String, Long> conteoArchivoNuevo = delArchivo.stream()
                .collect(Collectors.groupingBy(this::huella, Collectors.counting()));

        // Nuevos: cada fila del archivo consume, si existe, un "cupo" de una fila
        // existente con la misma huella. Lo que sobra sin cupo es genuinamente nuevo.
        Map<String, Integer> disponibles = existentesPorHuella.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
        List<Movimiento> nuevos = new ArrayList<>();
        for (Movimiento m : delArchivo) {
            String h = huella(m);
            int cupo = disponibles.getOrDefault(h, 0);
            if (cupo > 0) {
                disponibles.put(h, cupo - 1);
            } else {
                nuevos.add(m);
            }
        }

        // Desaparecidos: por cada huella existente, lo que sobra sobre lo que trae el
        // archivo nuevo. Ante ambigüedad (varios existentes, misma huella) se prioriza
        // dar de baja el que no tiene sugerencia activa, para no revertir sin necesidad
        // un cruce ya resuelto cuando basta con quitar el duplicado no tocado.
        List<Movimiento> desaparecidos = new ArrayList<>();
        for (Map.Entry<String, List<Movimiento>> entry : existentesPorHuella.entrySet()) {
            List<Movimiento> grupo = new ArrayList<>(entry.getValue());
            long enArchivoNuevo = conteoArchivoNuevo.getOrDefault(entry.getKey(), 0L);
            long sobran = grupo.size() - enArchivoNuevo;
            if (sobran <= 0) continue;

            if (grupo.size() > 1) {
                log.warn("Huella '{}' repetida en conciliacion={}: {} existentes, {} en archivo nuevo, "
                                + "{} se dan de baja",
                        entry.getKey(), idConciliacion, grupo.size(), enArchivoNuevo, sobran);
            }
            grupo.sort(Comparator.comparing(mv ->
                    sugerenciaRepo.buscarActivaPorMovimientoContable(mv.getId()).isPresent() ? 1 : 0));
            desaparecidos.addAll(grupo.subList(0, (int) sobran));
        }

        int revertidos = 0;
        for (Movimiento m : desaparecidos) {
            if (procesarAnulado(idConciliacion, m)) revertidos++;
        }

        log.info("Re-carga de auxiliar (conciliacion={}): {} nuevos, {} anulados ({} revertidos)",
                idConciliacion, nuevos.size(), desaparecidos.size(), revertidos);

        String jobId = null;
        if (!nuevos.isEmpty()) {
            List<Movimiento> persistidos = movimientoRepo.guardarContables(nuevos, idConciliacion);
            jobId = jobRepo.crearJob(idConciliacion);
            self.ejecutarMotorIncrementalAsync(jobId, idConciliacion, persistidos);
        }

        return ResumenCargaAuxiliar.builder()
                .jobId(jobId)
                .nuevos(nuevos.size())
                .anulados(desaparecidos.size())
                .revertidos(revertidos)
                .build();
    }

    /**
     * Da de baja un contable que desapareció del archivo re-subido. Si tiene una
     * sugerencia activa (PENDIENTE_REVISION o ACEPTADA) — sin importar que el propio
     * contable siga figurando como PENDIENTE en base de datos, ya que SUGERIDO nunca
     * se persiste (ver {@link com.conciliacion.bancaria.domain.service.ConciliationEngine})—
     * hay que revertir esa sugerencia primero: si no, borrar el contable directo viola
     * la FK de {@code sugerencias_conciliacion} (no tiene ON DELETE CASCADE).
     *
     * @return true si hubo una reversión real (sugerencia activa revertida), false si
     *         fue una baja simple sin ningún emparejamiento que deshacer.
     */
    private boolean procesarAnulado(Long idConciliacion, Movimiento contable) {
        Optional<Sugerencia> activa = sugerenciaRepo.buscarActivaPorMovimientoContable(contable.getId());
        if (activa.isEmpty()) {
            movimientoRepo.eliminarContablesPorIds(List.of(contable.getId()));
            return false;
        }

        Sugerencia sugerencia = activa.get();
        Long idBancario = sugerencia.getMovimientoBancario().getId();

        sugerenciaRepo.eliminarPorId(sugerencia.getId());
        reversionService.revertirAPendiente(idConciliacion, idBancario, "BANCARIO");
        movimientoRepo.eliminarContablesPorIds(List.of(contable.getId()));

        eventLog.contableRevertido(idConciliacion, contable.getId(), contable.getFecha(), contable.getMonto(),
                contable.getTipo(), contable.getDescripcion(), idBancario, sugerencia.getEstado().name());

        return true;
    }

    /**
     * Identidad de un movimiento contable entre recargas del auxiliar. Cuando el archivo
     * trae número de comprobante (ej. auxiliar SIESA, columna "Documento") se usa junto
     * con monto y tipo — nunca el comprobante solo. Un mismo número de comprobante puede
     * reaparecer en una recarga real con un monto distinto (anulación + reingreso por otro
     * valor, reutilizando el mismo Documento en SIESA); si la huella ignorara el monto, ese
     * caso colapsaría con el registro viejo en el diff por multiconjunto y la reversión
     * nunca se dispararía (bug real encontrado en producción). El monto de
     * {@link SiesaXlsParserService} siempre es positivo — la dirección va en {@code tipo},
     * no en el signo — así que no hace falta un monto con signo aparte, basta con incluir
     * ambos. El caso feliz (mismo comprobante + mismo monto + mismo tipo en una
     * re-exportación sin cambios) sigue tratándose como "ya existe", no como nuevo.
     *
     * Sin comprobante (auxiliar CSV genérico) se cae a un hash de texto normalizado
     * agresivamente (sin tildes, sin espacios múltiples, mayúsculas) para reducir falsos
     * "nuevos" causados por variaciones triviales de formato.
     */
    private String huella(Movimiento m) {
        String montoTipo = m.getMonto().stripTrailingZeros().toPlainString() + "|" + m.getTipo();
        if (m.getNumeroComprobante() != null && !m.getNumeroComprobante().isBlank()) {
            return "NC:" + m.getNumeroComprobante().trim() + "|" + montoTipo;
        }
        return "TXT:" + m.getFecha() + "|" + montoTipo + "|" + normalizarDescripcion(m.getDescripcion());
    }

    private String normalizarDescripcion(String descripcion) {
        if (descripcion == null) return "";
        String sinTildes = java.text.Normalizer.normalize(descripcion, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.trim().replaceAll("\\s+", " ").toUpperCase();
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
        self.ejecutarMotorAsync(jobId, idConciliacion);
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
