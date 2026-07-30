package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ColumnaPosicion;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfigAnchoFijo;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.service.parser.support.ContinuacionLineaHelper;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.LineasTextoReader;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.domain.service.parser.support.SignoResolver;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de extractos de texto de ancho fijo (reporte de impresión), dirigido
 * por columnas de posición de caracter en vez de la regex Java que este parser
 * reemplaza ({@code ExtractoBancarioTxtAnchoFijoParserService}).
 */
@RequiredArgsConstructor
public class AnchoFijoBankStatementParser {

    private final MontoParser montoParser;
    private final FechaResolver fechaResolver;
    private final SignoResolver signoResolver;
    private final ContinuacionLineaHelper continuacionHelper;
    private final CuadreValidator cuadreValidator;
    private final LineasTextoReader lineasReader;

    public List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo) {
        ConfigAnchoFijo c = config.getAnchoFijo();
        int anio = fechaResolver.extraerAnio(periodo);
        List<String> lineas = lineasReader.leer(contenido, config.getEncoding());

        List<Movimiento> movimientos = new ArrayList<>();

        for (String linea : lineas) {
            Map<String, String> valores = extraerValores(c, linea);
            LocalDate fecha = resolverFecha(valores, c, anio);

            if (fecha == null) {
                intentarContinuacion(movimientos, valores, config);
                continue;
            }

            MontoConTipo montoConTipo = resolverMontoConTipo(c, valores, config);
            if (montoConTipo == null) continue;

            String descripcion = valores.getOrDefault("descripcion", "").trim();
            movimientos.add(Movimiento.builder()
                    .fecha(fecha)
                    .descripcion(descripcion)
                    .monto(montoConTipo.monto())
                    .tipo(montoConTipo.tipo())
                    .estado(EstadoMovimiento.PENDIENTE)
                    .build());
        }

        return movimientos;
    }

    public ResultadoCuadre validarCuadre(byte[] contenido, ConfiguracionExtractoDetalle config) {
        List<String> lineas = lineasReader.leer(contenido, config.getEncoding());
        return cuadreValidator.validarEnTexto(lineas, config.getCuadre(),
                config.getSeparadorMiles(), config.getSeparadorDecimales());
    }

    // ── Extracción por posición ────────────────────────────────────────────

    private Map<String, String> extraerValores(ConfigAnchoFijo c, String linea) {
        Map<String, String> valores = new HashMap<>();
        if (c.getColumnas() == null) return valores;
        for (ColumnaPosicion columna : c.getColumnas()) {
            valores.put(columna.getCampo(), columna.extraer(linea));
        }
        return valores;
    }

    private LocalDate resolverFecha(Map<String, String> valores, ConfigAnchoFijo c, int anio) {
        String fechaCompleta = valores.get("fecha");
        if (fechaCompleta != null && !fechaCompleta.isBlank()) {
            return fechaResolver.resolverConFormato(fechaCompleta, c.getFormatoFecha(), anio);
        }
        String dia = valores.get("dia");
        String mes = valores.get("mes");
        if (dia != null && !dia.isBlank() && mes != null && !mes.isBlank()) {
            return fechaResolver.resolverDiaMes(dia, mes, anio);
        }
        return null;
    }

    private void intentarContinuacion(List<Movimiento> movimientos, Map<String, String> valores,
                                       ConfiguracionExtractoDetalle config) {
        if (movimientos.isEmpty()) return;
        if (!continuacionHelper.esContinuacion(valores, config.getContinuacion())) return;

        String campoDestino = config.getContinuacion().getCampoDestino();
        if (!"descripcion".equals(campoDestino)) return;

        String extra = valores.getOrDefault("descripcion", "").trim();
        if (extra.isEmpty()) return;

        int idx = movimientos.size() - 1;
        Movimiento anterior = movimientos.get(idx);
        String nuevaDescripcion = (anterior.getDescripcion() + " " + extra).trim();
        movimientos.set(idx, anterior.withDescripcion(nuevaDescripcion));
    }

    private MontoConTipo resolverMontoConTipo(ConfigAnchoFijo c, Map<String, String> valores,
                                               ConfiguracionExtractoDetalle config) {
        String sepMiles = config.getSeparadorMiles();
        String sepDecimales = config.getSeparadorDecimales();
        BigDecimal factor = config.getFactorMonto();

        switch (c.getConvencionSigno()) {
            case COLUMNAS_SEPARADAS -> {
                BigDecimal debito = montoParser.parsear(valores.get("debito"), sepMiles, sepDecimales, factor);
                BigDecimal credito = montoParser.parsear(valores.get("credito"), sepMiles, sepDecimales, factor);
                if (debito.compareTo(BigDecimal.ZERO) == 0 && credito.compareTo(BigDecimal.ZERO) == 0) return null;
                return debito.compareTo(BigDecimal.ZERO) > 0
                        ? new MontoConTipo(debito, "DEBITO")
                        : new MontoConTipo(credito, "CREDITO");
            }
            case COLUMNA_TIPO -> {
                BigDecimal monto = montoParser.parsear(valores.get("monto"), sepMiles, sepDecimales, factor);
                if (monto.compareTo(BigDecimal.ZERO) == 0) return null;
                boolean esCredito = signoResolver.esCreditoPorTipoLiteral(valores.get("tipo"));
                if (c.isInvertir()) esCredito = !esCredito;
                return new MontoConTipo(monto.abs(), signoResolver.tipoDe(esCredito));
            }
            default -> { // SUFIJO / PREFIJO
                String textoMonto = valores.get("monto");
                if (textoMonto == null || textoMonto.isBlank()) return null;
                boolean esCredito = signoResolver.esCreditoPorSigno(textoMonto, c.getConvencionSigno());
                if (c.isInvertir()) esCredito = !esCredito;
                String limpio = signoResolver.limpiarSigno(textoMonto, c.getConvencionSigno());
                BigDecimal monto = montoParser.parsear(limpio, sepMiles, sepDecimales, factor);
                if (monto.compareTo(BigDecimal.ZERO) == 0) return null;
                return new MontoConTipo(monto.abs(), signoResolver.tipoDe(esCredito));
            }
        }
    }

    private record MontoConTipo(BigDecimal monto, String tipo) { }
}
