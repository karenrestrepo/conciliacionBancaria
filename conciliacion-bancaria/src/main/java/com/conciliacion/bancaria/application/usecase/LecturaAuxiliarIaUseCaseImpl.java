package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.domain.model.ArchivoAuxiliar;
import com.conciliacion.bancaria.domain.model.AuxiliarLeido;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.MapeoAuxiliar;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoAuxiliar;
import com.conciliacion.bancaria.domain.model.ResultadoLectura.Estado;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;
import com.conciliacion.bancaria.domain.model.RevisionAuxiliar;
import com.conciliacion.bancaria.domain.model.RevisionLectura.EstadoCuadre;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.in.LecturaAuxiliarIaUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.LectorArchivoPort;
import com.conciliacion.bancaria.domain.port.out.LectorDocumentoAuxiliarIaPort;
import com.conciliacion.bancaria.domain.port.out.MapeadorAuxiliarIaPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.service.InterpreteAuxiliar;
import com.conciliacion.bancaria.domain.service.InterpreteAuxiliar.Interpretacion;
import com.conciliacion.bancaria.domain.service.MuestraTabla;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * La IA interpreta el archivo (dice qué hay en cada columna, o transcribe un documento); el código
 * lo verifica y decide si se puede cargar. No es transaccional: la IA no debe retener la base.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LecturaAuxiliarIaUseCaseImpl implements LecturaAuxiliarIaUseCase {

    private final LectorArchivoPort lectorArchivo;
    private final MapeadorAuxiliarIaPort mapeador;
    private final LectorDocumentoAuxiliarIaPort lectorDocumento;
    private final MuestraTabla muestraTabla;
    private final InterpreteAuxiliar interprete;
    private final ConciliacionRepositoryPort conciliacionRepo;
    private final MovimientoRepositoryPort movimientoRepo;
    private final CargaCsvUseCase cargaCsvUseCase;

    /** @param transcripcion solo cuando la IA leyó un documento */
    private record Intento(Interpretacion interpretacion, AuxiliarLeido transcripcion) {}

    @Override
    public ResultadoAuxiliar procesar(Long idConciliacion, byte[] contenido) {
        Conciliacion conciliacion = buscar(idConciliacion);
        ArchivoAuxiliar archivo = lectorArchivo.leer(contenido);

        Intento intento;
        boolean simulado;
        if (archivo.esTabla()) {
            TablaArchivo tabla = archivo.tabla();
            String muestra = muestraTabla.construir(tabla);
            intento = conRespaldo(
                    () -> new Intento(interprete.interpretar(tabla, mapeador.mapear(muestra)), null),
                    mapeador.tieneRespaldo(),
                    () -> new Intento(interprete.interpretar(tabla, mapeador.mapearConRespaldo(muestra)), null));
            simulado = mapeador.esSimulado();
        } else {
            ArchivoAuxiliar.Documento documento = archivo.documento();
            String periodo = conciliacion.getPeriodo();
            intento = conRespaldo(
                    () -> transcrito(lectorDocumento.leer(documento, periodo)),
                    lectorDocumento.tieneRespaldo(),
                    () -> transcrito(lectorDocumento.leerConRespaldo(documento, periodo)));
            simulado = lectorDocumento.esSimulado();
        }
        Interpretacion lectura = intento.interpretacion();
        RevisionAuxiliar revision = lectura.revision();

        if (!revision.getErrores().isEmpty()) {
            return resultado(Estado.RECHAZADO, revision,
                    "No se pudo leer el auxiliar completo. No se cargó nada");
        }
        if (revision.getEstadoCuadre() == EstadoCuadre.NO_CUADRA) {
            return resultado(Estado.RECHAZADO, revision,
                    "La lectura no coincide con los saldos o totales del auxiliar. No se cargó nada");
        }
        List<Movimiento> existentes = movimientoRepo.buscarTodosContablesPorConciliacion(idConciliacion);
        if (simulado) {
            return porConfirmar(intento, existentes,
                    "Modo de prueba: no se usó IA, los datos son de ejemplo o se asumió un formato");
        }
        if (revision.getEstadoCuadre() == EstadoCuadre.NO_VERIFICABLE) {
            return porConfirmar(intento, existentes,
                    "El archivo no trae saldos o totales para verificar la lectura");
        }
        if (pareceDeOtroPeriodo(lectura.movimientos(), conciliacion.getPeriodo())) {
            return porConfirmar(intento, existentes, "La mayoría de los movimientos no son del período "
                    + conciliacion.getPeriodo() + ". Verifique el archivo");
        }

        ResumenCargaAuxiliar resumen = cargar(idConciliacion, lectura.movimientos(), existentes);
        return ResultadoAuxiliar.builder()
                .estado(Estado.CARGADO)
                .mensaje("Se leyeron " + lectura.movimientos().size() + " movimientos y el auxiliar cuadra")
                .revision(revision)
                .resumen(resumen)
                .build();
    }

    @Override
    public ResumenCargaAuxiliar confirmar(Long idConciliacion, byte[] contenido, MapeoAuxiliar mapeo,
                                          AuxiliarLeido transcripcion) {
        buscar(idConciliacion);
        Interpretacion lectura;
        if (transcripcion != null) {
            lectura = interprete.interpretar(transcripcion);
        } else {
            ArchivoAuxiliar archivo = lectorArchivo.leer(contenido);
            if (!archivo.esTabla()) {
                throw new CsvValidationException("Faltan los datos de la lectura. Suba el archivo de nuevo");
            }
            lectura = interprete.interpretar(archivo.tabla(), mapeo);
        }
        RevisionAuxiliar revision = lectura.revision();
        if (!revision.getErrores().isEmpty()) {
            throw new CsvValidationException("No se puede cargar: " + String.join("; ", revision.getErrores()));
        }
        if (revision.getEstadoCuadre() == EstadoCuadre.NO_CUADRA) {
            throw new CsvValidationException("No se puede cargar: el auxiliar no cuadra");
        }
        return cargar(idConciliacion, lectura.movimientos(),
                movimientoRepo.buscarTodosContablesPorConciliacion(idConciliacion));
    }

    private Intento transcrito(AuxiliarLeido leido) {
        return new Intento(interprete.interpretar(leido), leido);
    }

    /** Si la primera lectura tiene errores o no cuadra, prueba con el modelo de respaldo. */
    private Intento conRespaldo(Supplier<Intento> primera, boolean hayRespaldo, Supplier<Intento> segunda) {
        Intento inicial = primera.get();
        if (calidad(inicial.interpretacion()) >= 2 || !hayRespaldo) {
            return inicial;
        }
        log.info("La primera lectura del auxiliar no superó la verificación; se reintenta con el modelo de respaldo");
        try {
            Intento reintento = segunda.get();
            return calidad(reintento.interpretacion()) >= calidad(inicial.interpretacion()) ? reintento : inicial;
        } catch (CsvValidationException e) {
            log.warn("El reintento falló: {}", e.getMessage());
            return inicial;
        }
    }

    /** 3 cuadra, 2 no verificable, 1 no cuadra, 0 con errores. */
    private int calidad(Interpretacion lectura) {
        if (!lectura.revision().getErrores().isEmpty()) return 0;
        return switch (lectura.revision().getEstadoCuadre()) {
            case CUADRA -> 3;
            case NO_VERIFICABLE -> 2;
            case NO_CUADRA -> 1;
        };
    }

    private boolean pareceDeOtroPeriodo(List<Movimiento> movimientos, String periodo) {
        long fuera = movimientos.stream()
                .filter(m -> !YearMonth.from(m.getFecha()).toString().equals(periodo))
                .count();
        return fuera * 2 > movimientos.size();
    }

    private ResumenCargaAuxiliar cargar(Long idConciliacion, List<Movimiento> leidos, List<Movimiento> existentes) {
        return cargaCsvUseCase.cargarAuxiliarLeido(idConciliacion, alinear(leidos, existentes));
    }

    /**
     * Al volver a subir un auxiliar, lo que desaparece se anula. Para que una diferencia en cómo se
     * leyó la descripción o el comprobante no anule cruces ya hechos, cada movimiento que coincide
     * en fecha, valor y tipo con uno guardado conserva la identidad del guardado.
     */
    private List<Movimiento> alinear(List<Movimiento> leidos, List<Movimiento> existentes) {
        List<Movimiento> libres = new ArrayList<>(existentes);
        Movimiento[] resultado = leidos.toArray(new Movimiento[0]);
        boolean[] alineado = new boolean[resultado.length];
        // Primero las coincidencias exactas; después las que no tienen comprobante en algún lado.
        alinearSi(resultado, alineado, libres,
                (nuevo, viejo) -> Objects.equals(nuevo.getNumeroComprobante(), viejo.getNumeroComprobante())
                        && Objects.equals(nuevo.getDescripcion(), viejo.getDescripcion()));
        alinearSi(resultado, alineado, libres, (nuevo, viejo) -> nuevo.getNumeroComprobante() != null
                && nuevo.getNumeroComprobante().equals(viejo.getNumeroComprobante()));
        alinearSi(resultado, alineado, libres,
                (nuevo, viejo) -> nuevo.getNumeroComprobante() == null || viejo.getNumeroComprobante() == null);
        return List.of(resultado);
    }

    private void alinearSi(Movimiento[] movimientos, boolean[] alineado, List<Movimiento> libres,
                           BiPredicate<Movimiento, Movimiento> criterio) {
        for (int i = 0; i < movimientos.length; i++) {
            if (alineado[i]) continue;
            Movimiento nuevo = movimientos[i];
            for (int j = 0; j < libres.size(); j++) {
                Movimiento viejo = libres.get(j);
                if (nuevo.getFecha().equals(viejo.getFecha())
                        && nuevo.getTipo().equals(viejo.getTipo())
                        && nuevo.getMonto().compareTo(viejo.getMonto()) == 0
                        && criterio.test(nuevo, viejo)) {
                    movimientos[i] = nuevo
                            .withDescripcion(viejo.getDescripcion())
                            .withNumeroComprobante(viejo.getNumeroComprobante());
                    alineado[i] = true;
                    libres.remove(j);
                    break;
                }
            }
        }
    }

    private ResultadoAuxiliar porConfirmar(Intento intento, List<Movimiento> existentes, String mensaje) {
        RevisionAuxiliar revision = intento.interpretacion().revision();
        if (!existentes.isEmpty()) {
            List<String> advertencias = new ArrayList<>(revision.getAdvertencias());
            advertencias.add("Esta conciliación ya tiene movimientos del auxiliar: "
                    + "los que no estén en este archivo se anularán");
            revision = revision.toBuilder().advertencias(advertencias).build();
        }
        return ResultadoAuxiliar.builder()
                .estado(Estado.REQUIERE_CONFIRMACION).revision(revision).mensaje(mensaje)
                .lectura(intento.transcripcion())
                .build();
    }

    private ResultadoAuxiliar resultado(Estado estado, RevisionAuxiliar revision, String mensaje) {
        return ResultadoAuxiliar.builder().estado(estado).revision(revision).mensaje(mensaje).build();
    }

    private Conciliacion buscar(Long idConciliacion) {
        return conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la conciliación " + idConciliacion));
    }
}
