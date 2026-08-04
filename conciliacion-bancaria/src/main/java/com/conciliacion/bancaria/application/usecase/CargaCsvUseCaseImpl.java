package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;
import com.conciliacion.bancaria.domain.model.Sugerencia;
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
import com.conciliacion.bancaria.domain.service.MovimientoReversionService;
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
import java.util.Optional;
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

    /**
     * Reconciliación final de partidas pendientes: borra cualquier partida PENDIENTE cuyo
     * movimiento ya tenga, EN ESTE MOMENTO, una sugerencia activa (PENDIENTE_REVISION o
     * ACEPTADA) — sin importar si esa sugerencia la generó este mismo job o el otro.
     *
     * Necesaria porque {@code ejecutarMotorAsync} (disparado al subir el extracto) y
     * {@code ejecutarMotorIncrementalAsync} (disparado al subir el auxiliar) corren de
     * verdad en paralelo en el mismo pool ({@code conciliacionExecutor}) desde que se
     * corrigió el bug de auto-invocación de {@code @Async}. Cada uno calcula su propio
     * resultado sobre una FOTO de bancarios/contables leída en su propio momento — si
     * el motor del extracto lee 0 contables porque el auxiliar todavía no había hecho
     * commit, genera partidas pendientes para TODOS los bancarios; si esa inserción
     * ocurre después de que el motor del auxiliar ya intentó limpiar la partida del
     * bancario que sí emparejó (intento que no borra nada porque la partida todavía no
     * existía), esa partida queda huérfana para siempre — bug real reproducido con 328
     * bancarios, 321 sugerencias generadas correctamente y 328 pendientes fantasma.
     *
     * Al llamarse SIEMPRE al final de ambos jobs, cualquiera de los dos que termine
     * después vuelve a limpiar contra el estado más reciente de sugerencias, cerrando la
     * carrera sin importar el orden real en que terminen.
     */
    private void limpiarPendientesConSugerenciaActiva(Long idConciliacion) {
        sugerenciaRepo.buscarBancarioIdsConSugerenciaActiva(idConciliacion)
                .forEach(id -> partidaRepo.eliminarPendientePorMovimiento(id, "BANCARIO"));
        sugerenciaRepo.buscarContableIdsConSugerenciaActiva(idConciliacion)
                .forEach(id -> partidaRepo.eliminarPendientePorMovimiento(id, "CONTABLE"));
    }

    private boolean esArchivoXls(String nombre) {
        if (nombre == null) return false;
        String lower = nombre.toLowerCase();
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    /**
     * Persistencia atómica del resultado del motor incremental: sugerencias, limpieza de
     * la partida pendiente de cada bancario emparejado, partidas de contables sin par, y
     * la reconciliación final -- todo en una sola transacción. Si cualquier paso falla, se
     * revierte TODO en vez de quedar a medias.
     *
     * Necesario porque este método corre dentro de {@code ejecutarMotorIncrementalAsync},
     * que es {@code @Async} y por lo tanto NO hereda ninguna transacción ambiental del
     * método que lo invocó -- las consultas {@code @Modifying} de aquí adentro (como
     * {@code eliminarPendientePorMovimiento}, usada tanto en la limpieza explícita como en
     * {@link #limpiarPendientesConSugerenciaActiva}) requieren una transacción activa para
     * ejecutar, algo que {@code save}/{@code saveAll} obtienen automáticamente pero
     * {@code @Modifying @Query} no. Sin esta transacción, la corrida real que motivó este
     * fix llegó a guardar 321 sugerencias con éxito (guardarTodas corre en su propia
     * transacción implícita) pero falló con {@code TransactionRequiredException} justo al
     * intentar la primera limpieza, dejando 328 partidas pendientes intactas -- exactamente
     * el estado a medias que esta transacción única evita hacia adelante.
     *
     * Es {@code public} (no {@code private}) y se invoca vía {@code self.} por la misma
     * razón que {@code ejecutarMotorIncrementalAsync}: el proxy de Spring que aplica
     * {@code @Transactional} sólo intercepta llamadas que pasan por él, no invocaciones
     * directas dentro de la misma instancia.
     */
    @Transactional
    public void persistirResultadoMotorIncremental(Long idConciliacion,
                                                    ConciliationEngine.ResultadoMotor resultado) {
        sugerenciaRepo.guardarTodas(resultado.sugerencias());

        // Los bancarios que obtuvieron sugerencia ya no son pendientes; eliminar su partida
        resultado.sugerencias().stream()
                .map(s -> s.getMovimientoBancario().getId())
                .distinct()
                .forEach(id -> partidaRepo.eliminarPendientePorMovimiento(id, "BANCARIO"));

        // Guardar sólo partidas de contables nuevos que no encontraron par
        partidaRepo.guardarTodas(sinPendienteExistente(idConciliacion, resultado.partidasContables(), "CONTABLE"));

        limpiarPendientesConSugerenciaActiva(idConciliacion);
    }

    /** Misma razón de ser que {@link #persistirResultadoMotorIncremental} para la rama sin bancarios libres. */
    @Transactional
    public void persistirSoloContablesIncremental(Long idConciliacion, List<Movimiento> nuevosContables) {
        List<com.conciliacion.bancaria.domain.model.PartidaConciliatoria> partidas =
                nuevosContables.stream()
                        .map(c -> com.conciliacion.bancaria.domain.model.PartidaConciliatoria.builder()
                                .idConciliacion(idConciliacion)
                                .idMovimiento(c.getId())
                                .tipoOrigen("CONTABLE")
                                .estado("PENDIENTE")
                                .build())
                        .toList();
        partidaRepo.guardarTodas(sinPendienteExistente(idConciliacion, partidas, "CONTABLE"));
        limpiarPendientesConSugerenciaActiva(idConciliacion);
    }

    /**
     * Persistencia atómica del resultado del motor completo -- misma razón de ser que
     * {@link #persistirResultadoMotorIncremental}, para {@code ejecutarMotorAsync}.
     */
    @Transactional
    public void persistirResultadoMotorCompleto(Long idConciliacion,
                                                 ConciliationEngine.ResultadoMotor resultado) {
        sugerenciaRepo.guardarTodas(resultado.sugerencias());
        partidaRepo.guardarTodas(sinPendienteExistente(idConciliacion, resultado.partidasBancarias(), "BANCARIO"));
        partidaRepo.guardarTodas(sinPendienteExistente(idConciliacion, resultado.partidasContables(), "CONTABLE"));
        limpiarPendientesConSugerenciaActiva(idConciliacion);
    }

    /**
     * Filtra los movimientos que YA tienen una partida PENDIENTE para esta conciliación y
     * este tipoOrigen, antes de insertar. Necesario porque {@code ejecutarMotorAsync} relee
     * TODOS los bancarios/contables no conciliados de la conciliación en cada corrida (no
     * sólo los nuevos) -- en una conciliación con auxiliar_conjunto (tarjetas de crédito),
     * cada extracto adicional que se sube dispara el motor completo sobre lo ya acumulado,
     * y {@link ConciliationEngine#ejecutar} genera una partida PENDIENTE por cada bancario
     * sin par SIN saber que ya existe una de una corrida anterior -- guardarTodas() hace
     * saveAll() sobre entidades sin id, así que JPA siempre inserta filas nuevas. Bug real:
     * subir 5 extractos de tarjeta antes de cargar el auxiliar dejaba al movimiento del
     * primer archivo con hasta 4 partidas PENDIENTE duplicadas.
     */
    private List<com.conciliacion.bancaria.domain.model.PartidaConciliatoria> sinPendienteExistente(
            Long idConciliacion,
            List<com.conciliacion.bancaria.domain.model.PartidaConciliatoria> partidas,
            String tipoOrigen) {
        if (partidas.isEmpty()) return partidas;
        Set<Long> yaPendientes = partidaRepo.buscarIdsMovimientoConPendiente(idConciliacion, tipoOrigen);
        if (yaPendientes.isEmpty()) return partidas;
        return partidas.stream()
                .filter(p -> !yaPendientes.contains(p.getIdMovimiento()))
                .toList();
    }

    // Motor incremental — sólo compara nuevos contables contra bancarios sin sugerencia activa
    //
    // Deliberadamente NO lleva @Transactional en este método -- las llamadas a
    // jobRepo.actualizarProgreso deben seguir comiteando cada una por separado (como ya
    // hacen save/saveAll) para que el polling del frontend vea progreso incremental en vivo;
    // envolver todo el método en una sola transacción dejaría esos updates invisibles para
    // otras conexiones hasta el commit final. Sólo los pasos que escriben resultado del
    // motor (self.persistirResultadoMotorIncremental/persistirSoloContablesIncremental) son
    // transaccionales, y punto: el resto son lecturas o saves que ya son atómicos por sí solos.
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
                self.persistirSoloContablesIncremental(idConciliacion, nuevosContables);
                jobRepo.completar(jobId);
                return;
            }

            ConciliationEngine.ResultadoMotor resultado =
                    conciliationEngine.ejecutar(idConciliacion, bancariosLibres, nuevosContables);
            jobRepo.actualizarProgreso(jobId, 75);

            self.persistirResultadoMotorIncremental(idConciliacion, resultado);

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

    // Motor asíncrono — se ejecuta en el pool "conciliacionExecutor". Ver el comentario sobre
    // @Transactional en ejecutarMotorIncrementalAsync -- misma razón para no anotar este método.
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

            self.persistirResultadoMotorCompleto(idConciliacion, resultado);

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
