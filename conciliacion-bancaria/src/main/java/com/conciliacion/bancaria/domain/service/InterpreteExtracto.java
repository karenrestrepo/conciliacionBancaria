package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.MapeoColumnas;
import com.conciliacion.bancaria.domain.model.MapeoExtracto;
import com.conciliacion.bancaria.domain.model.RevisionLectura;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import lombok.RequiredArgsConstructor;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee un extracto en Excel o texto siguiendo el {@link MapeoExtracto} y lo verifica con las mismas
 * reglas que un extracto en PDF. No usa IA.
 */
@RequiredArgsConstructor
public class InterpreteExtracto {

    private final InterpreteAuxiliar lectorDeTablas;
    private final RevisorLecturaExtracto revisor;

    public RevisionLectura interpretar(TablaArchivo tabla, MapeoExtracto mapeo, String periodo) {
        var lectura = lectorDeTablas.interpretar(tabla, aColumnas(mapeo), YearMonth.parse(periodo));
        var leido = lectura.revision();

        RevisionLectura revision = revisor.revisar(LecturaExtracto.builder()
                .tipoProducto(mapeo.getTipoProducto())
                .ultimosDigitosTarjeta(mapeo.getUltimosDigitosTarjeta())
                .saldoAnterior(leido.getSaldoInicial())
                .saldoFinal(leido.getSaldoFinal())
                .totalCreditos(leido.getTotalDebitosArchivo())
                .totalDebitos(leido.getTotalCreditosArchivo())
                .movimientos(leido.getMovimientos())
                .build(), periodo);

        // Lo que no se pudo leer de la tabla también impide o advierte.
        List<String> errores = new ArrayList<>(leido.getErrores());
        errores.addAll(revision.getErrores());
        List<String> advertencias = new ArrayList<>(revision.getAdvertencias());
        if (revision.getEstadoCuadre() != RevisionLectura.EstadoCuadre.CUADRA) {
            leido.getAdvertencias().stream()
                    .filter(a -> a.contains("no se incluyeron"))
                    .forEach(advertencias::add);
        }
        return revision.toBuilder().errores(errores).advertencias(advertencias).build();
    }

    /**
     * El lector de tablas habla en términos del auxiliar, donde el débito es dinero que entra:
     * por eso los abonos del banco ocupan el lugar del débito contable.
     */
    private MapeoColumnas aColumnas(MapeoExtracto m) {
        return MapeoColumnas.builder()
                .hoja(m.getHoja()).primeraFilaDatos(m.getPrimeraFilaDatos())
                .columnaFecha(m.getColumnaFecha()).columnaComprobante(-1)
                .columnasDescripcion(m.getColumnasDescripcion())
                .esquemaMonto("ABONO_CARGO".equals(m.getEsquemaMonto()) ? "DEBITO_CREDITO" : m.getEsquemaMonto())
                .columnaDebito(m.getColumnaAbonos()).columnaCredito(m.getColumnaCargos())
                .columnaMonto(m.getColumnaMonto()).columnaNaturaleza(m.getColumnaNaturaleza())
                .valoresDebito(m.getValoresAbono()).positivoEsDebito(m.isPositivoEsAbono())
                .separadorDecimal(m.getSeparadorDecimal()).ordenFecha(m.getOrdenFecha())
                .saldoInicial(m.getSaldoAnterior()).saldoFinal(m.getSaldoFinal())
                .totalDebitos(m.getTotalAbonos()).totalCreditos(m.getTotalCargos())
                .build();
    }
}
