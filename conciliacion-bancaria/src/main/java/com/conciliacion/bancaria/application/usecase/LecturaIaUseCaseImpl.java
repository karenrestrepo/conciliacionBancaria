package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.domain.model.ArchivoLeido;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoLectura;
import com.conciliacion.bancaria.domain.model.RevisionLectura;
import com.conciliacion.bancaria.domain.model.RevisionLectura.EstadoCuadre;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.in.LecturaIaUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.LectorArchivoPort;
import com.conciliacion.bancaria.domain.port.out.LectorExtractoIaPort;
import com.conciliacion.bancaria.domain.port.out.MapeadorExtractoIaPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.service.InterpreteExtracto;
import com.conciliacion.bancaria.domain.service.MuestraTabla;
import com.conciliacion.bancaria.domain.service.RevisorLecturaExtracto;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * La IA interpreta el documento; este caso de uso decide con reglas fijas si se puede guardar.
 * No es transaccional a propósito: la llamada a la IA puede tardar minutos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LecturaIaUseCaseImpl implements LecturaIaUseCase {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private final LectorArchivoPort lectorArchivo;
    private final LectorExtractoIaPort lector;
    private final MapeadorExtractoIaPort mapeador;
    private final MuestraTabla muestraTabla;
    private final InterpreteExtracto interpreteExtracto;
    private final RevisorLecturaExtracto revisor;
    private final ConciliacionRepositoryPort conciliacionRepo;
    private final MovimientoRepositoryPort movimientoRepo;
    private final CargaCsvUseCase cargaCsvUseCase;

    @Override
    public ResultadoLectura procesar(Long idConciliacion, byte[] contenido) {
        Conciliacion conciliacion = buscar(idConciliacion);
        String periodo = conciliacion.getPeriodo();
        ArchivoLeido archivo = lectorArchivo.leer(contenido);

        RevisionLectura revision;
        boolean simulado;
        if (archivo.esTabla()) {
            TablaArchivo tabla = archivo.tabla();
            String muestra = muestraTabla.construir(tabla);
            revision = conRespaldo(
                    () -> interpreteExtracto.interpretar(tabla, mapeador.mapear(muestra, periodo), periodo),
                    mapeador.tieneRespaldo(),
                    () -> interpreteExtracto.interpretar(
                            tabla, mapeador.mapearConRespaldo(muestra, periodo), periodo));
            simulado = mapeador.esSimulado();
        } else {
            ArchivoLeido.Documento documento = archivo.documento();
            revision = conRespaldo(
                    () -> revisor.revisar(lector.leer(documento, periodo), periodo),
                    lector.tieneRespaldo(),
                    () -> revisor.revisar(lector.leerConRespaldo(documento, periodo), periodo));
            simulado = lector.esSimulado();
        }

        if (!revision.getErrores().isEmpty()) {
            return rechazado(revision, "La lectura del extracto está incompleta. No se cargó nada");
        }
        if (revision.getEstadoCuadre() == EstadoCuadre.NO_CUADRA) {
            return rechazado(revision, mensajeDescuadre(revision));
        }
        if (simulado) {
            return porConfirmar(conAdvertenciaDeSimulacion(revision),
                    "Modo de prueba: no se usó IA, los datos son de ejemplo o se asumió un formato");
        }
        List<Movimiento> movimientos = aMovimientos(revision.getLectura());
        if (yaCargado(idConciliacion, movimientos)) {
            return rechazado(revision, "Este extracto ya está cargado en la conciliación. No se agregó nada");
        }
        if (revision.getEstadoCuadre() == EstadoCuadre.NO_VERIFICABLE) {
            return porConfirmar(revision, "No se pudo verificar el cuadre de este extracto");
        }
        if (pareceDeOtroPeriodo(revision.getLectura(), periodo)) {
            return porConfirmar(revision,
                    "La mayoría de los movimientos no son del período " + periodo + ". Verifique el archivo");
        }

        String jobId = cargaCsvUseCase.confirmarExtractoLeido(idConciliacion, movimientos);
        return ResultadoLectura.builder()
                .estado(ResultadoLectura.Estado.CARGADO)
                .mensaje("Se cargaron " + movimientos.size() + " movimientos y el extracto cuadra")
                .revision(revision)
                .jobId(jobId)
                .build();
    }

    @Override
    public String confirmar(Long idConciliacion, LecturaExtracto lectura, boolean aceptaSinCuadre) {
        Conciliacion conciliacion = buscar(idConciliacion);
        RevisionLectura revision = revisor.revisar(lectura, conciliacion.getPeriodo());

        if (!revision.getErrores().isEmpty()) {
            throw new CsvValidationException("No se puede aprobar: " + String.join("; ", revision.getErrores()));
        }
        if (revision.getEstadoCuadre() == EstadoCuadre.NO_CUADRA) {
            throw new CsvValidationException("No se puede aprobar: el extracto no cuadra");
        }
        if (revision.getEstadoCuadre() == EstadoCuadre.NO_VERIFICABLE && !aceptaSinCuadre) {
            throw new CsvValidationException(
                    "El cuadre no se puede verificar. Confirme que desea cargar el extracto");
        }

        List<Movimiento> movimientos = aMovimientos(lectura);
        if (yaCargado(idConciliacion, movimientos)) {
            throw new CsvValidationException("Este extracto ya está cargado en la conciliación");
        }
        return cargaCsvUseCase.confirmarExtractoLeido(idConciliacion, movimientos);
    }

    /** Si la primera lectura falla la verificación, prueba con el modelo de respaldo. */
    private RevisionLectura conRespaldo(Supplier<RevisionLectura> primera, boolean hayRespaldo,
                                        Supplier<RevisionLectura> segunda) {
        RevisionLectura inicial = primera.get();
        if (!hayRespaldo || !convieneReintentar(inicial)) {
            return inicial;
        }
        log.info("La primera lectura no superó la verificación; se reintenta con el modelo de respaldo");
        try {
            RevisionLectura reintento = segunda.get();
            return calidad(reintento) >= calidad(inicial) ? reintento : inicial;
        } catch (CsvValidationException e) {
            log.warn("El reintento falló: {}", e.getMessage());
            return inicial;
        }
    }

    /** Errores o saldos que no coinciden pueden deberse a una mala lectura. */
    private boolean convieneReintentar(RevisionLectura revision) {
        return !revision.getErrores().isEmpty()
                || revision.getEstadoCuadre() == EstadoCuadre.NO_CUADRA
                || (revision.getEstadoCuadre() == EstadoCuadre.NO_VERIFICABLE
                    && revision.getDiferencia() != null
                    && revision.getDiferencia().compareTo(TOLERANCIA) > 0);
    }

    private int calidad(RevisionLectura revision) {
        if (!revision.getErrores().isEmpty()) return 0;
        return switch (revision.getEstadoCuadre()) {
            case CUADRA -> 3;
            case NO_VERIFICABLE -> 2;
            case NO_CUADRA -> 1;
        };
    }

    /** En tarjetas es normal que el corte abarque dos meses. */
    private boolean pareceDeOtroPeriodo(LecturaExtracto lectura, String periodo) {
        if ("TARJETA_CREDITO".equals(lectura.getTipoProducto())) return false;
        long fuera = lectura.getMovimientos().stream()
                .filter(m -> !YearMonth.from(LocalDate.parse(m.getFecha())).toString().equals(periodo))
                .count();
        return fuera * 2 > lectura.getMovimientos().size();
    }

    /**
     * Solo las tarjetas con auxiliar conjunto acumulan extractos (en las demás, el nuevo reemplaza
     * al anterior). Se considera repetido si todos sus movimientos ya están guardados.
     */
    private boolean yaCargado(Long idConciliacion, List<Movimiento> nuevos) {
        if (!conciliacionRepo.esAuxiliarConjunto(idConciliacion)) return false;
        Map<String, Integer> existentes = new HashMap<>();
        movimientoRepo.buscarTodosBancariosPorConciliacion(idConciliacion)
                .forEach(m -> existentes.merge(huella(m), 1, Integer::sum));
        for (Movimiento m : nuevos) {
            String huella = huella(m);
            int disponibles = existentes.getOrDefault(huella, 0);
            if (disponibles == 0) return false;
            existentes.put(huella, disponibles - 1);
        }
        return true;
    }

    /** Sin la descripción: la IA puede transcribirla distinto en dos lecturas. */
    private String huella(Movimiento m) {
        return m.getFecha() + "|" + m.getMonto().stripTrailingZeros().toPlainString()
                + "|" + m.getTipo() + "|" + m.getUltimosDigitosTarjeta();
    }

    private List<Movimiento> aMovimientos(LecturaExtracto lectura) {
        String tarjeta = "TARJETA_CREDITO".equals(lectura.getTipoProducto())
                && lectura.getUltimosDigitosTarjeta() != null
                && lectura.getUltimosDigitosTarjeta().matches("\\d{4}")
                ? lectura.getUltimosDigitosTarjeta() : null;

        return lectura.getMovimientos().stream()
                .map(m -> Movimiento.builder()
                        .fecha(LocalDate.parse(m.getFecha()))
                        .descripcion(m.getDescripcion().trim())
                        .monto(m.getMonto())
                        .tipo(m.getTipo())
                        .estado(EstadoMovimiento.PENDIENTE)
                        .ultimosDigitosTarjeta(tarjeta)
                        .build())
                .toList();
    }

    private String mensajeDescuadre(RevisionLectura revision) {
        BigDecimal diferencia = revision.getDiferencia();
        String detalle = diferencia != null && diferencia.compareTo(TOLERANCIA) > 0
                ? " La diferencia es de " + diferencia.toPlainString() + "."
                : "";
        return "La lectura no coincide con los saldos o totales del extracto." + detalle + " No se cargó nada";
    }

    private RevisionLectura conAdvertenciaDeSimulacion(RevisionLectura revision) {
        List<String> advertencias = new ArrayList<>();
        advertencias.add("DATOS SIMULADOS: no corresponden al archivo subido. No los apruebe en una conciliación real");
        advertencias.addAll(revision.getAdvertencias());
        return revision.toBuilder().advertencias(advertencias).build();
    }

    private ResultadoLectura rechazado(RevisionLectura revision, String mensaje) {
        return resultado(ResultadoLectura.Estado.RECHAZADO, revision, mensaje);
    }

    private ResultadoLectura porConfirmar(RevisionLectura revision, String mensaje) {
        return resultado(ResultadoLectura.Estado.REQUIERE_CONFIRMACION, revision, mensaje);
    }

    private ResultadoLectura resultado(ResultadoLectura.Estado estado, RevisionLectura revision, String mensaje) {
        return ResultadoLectura.builder().estado(estado).revision(revision).mensaje(mensaje).build();
    }

    private Conciliacion buscar(Long idConciliacion) {
        return conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la conciliación " + idConciliacion));
    }
}
