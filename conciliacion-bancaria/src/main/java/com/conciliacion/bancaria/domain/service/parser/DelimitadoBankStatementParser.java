package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfigDelimitado;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.LineasTextoReader;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.domain.service.parser.support.SignoResolver;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser de extractos delimitados (CSV / TXT con separador), dirigido por
 * índice de columna en vez de nombre de encabezado — cubre el rol que antes
 * tenía {@code CsvValidatorService} para extractos bancarios (el CSV del
 * libro auxiliar contable sigue usando ese servicio tal cual, sin cambios).
 */
@RequiredArgsConstructor
public class DelimitadoBankStatementParser {

    private final MontoParser montoParser;
    private final FechaResolver fechaResolver;
    private final SignoResolver signoResolver;
    private final CuadreValidator cuadreValidator;
    private final LineasTextoReader lineasReader;

    public List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo) {
        ConfigDelimitado c = config.getDelimitado();
        int anio = fechaResolver.extraerAnio(periodo);
        Charset charset = resolverCharset(config.getEncoding());
        char separador = resolverSeparador(c.getSeparador());

        List<Movimiento> movimientos = new ArrayList<>();

        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(new ByteArrayInputStream(contenido), charset))
                .withCSVParser(new CSVParserBuilder().withSeparator(separador).build())
                .build()) {

            String[] fila;
            int numeroFila = 0;
            while ((fila = reader.readNext()) != null) {
                if (numeroFila++ < c.getFilasASaltar()) continue;
                if (filaVacia(fila)) continue;

                Movimiento movimiento = parsearFila(fila, c, config, anio);
                if (movimiento != null) movimientos.add(movimiento);
            }
        } catch (CsvValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvValidationException("Error al leer el extracto: " + e.getMessage());
        }

        return movimientos;
    }

    public ResultadoCuadre validarCuadre(byte[] contenido, ConfiguracionExtractoDetalle config) {
        List<String> lineas = lineasReader.leer(contenido, config.getEncoding());
        return cuadreValidator.validarEnTexto(lineas, config.getCuadre(),
                config.getSeparadorMiles(), config.getSeparadorDecimales());
    }

    private Movimiento parsearFila(String[] fila, ConfigDelimitado c,
                                    ConfiguracionExtractoDetalle config, int anio) {
        String textoFecha = campo(fila, c.getColumnaFecha());
        if (textoFecha == null || textoFecha.isBlank()) return null;

        LocalDate fecha = fechaResolver.resolverConFormato(textoFecha, c.getFormatoFecha(), anio);
        if (fecha == null) return null;

        String descripcion = construirDescripcion(fila, c.getColumnaDescripcion(), c.getColumnaReferencia());

        if (c.getColumnaTipoMovimiento() >= 0) {
            BigDecimal monto = montoParser.parsear(campo(fila, c.getColumnaMonto()),
                    config.getSeparadorMiles(), config.getSeparadorDecimales(), config.getFactorMonto());
            if (monto.compareTo(BigDecimal.ZERO) == 0) return null;
            boolean esCredito = signoResolver.esCreditoPorTipoLiteral(campo(fila, c.getColumnaTipoMovimiento()));
            return build(fecha, descripcion, monto.abs(), signoResolver.tipoDe(esCredito));
        }

        if (c.isDebitoYCreditoSeparados()) {
            BigDecimal debito = montoParser.parsear(campo(fila, c.getColumnaDebito()),
                    config.getSeparadorMiles(), config.getSeparadorDecimales(), config.getFactorMonto());
            BigDecimal credito = montoParser.parsear(campo(fila, c.getColumnaCredito()),
                    config.getSeparadorMiles(), config.getSeparadorDecimales(), config.getFactorMonto());
            if (debito.compareTo(BigDecimal.ZERO) == 0 && credito.compareTo(BigDecimal.ZERO) == 0) return null;
            return debito.compareTo(BigDecimal.ZERO) > 0
                    ? build(fecha, descripcion, debito, "DEBITO")
                    : build(fecha, descripcion, credito, "CREDITO");
        }

        BigDecimal monto = montoParser.parsear(campo(fila, c.getColumnaMonto()),
                config.getSeparadorMiles(), config.getSeparadorDecimales(), config.getFactorMonto());
        if (monto.compareTo(BigDecimal.ZERO) == 0) return null;
        String tipo = monto.compareTo(BigDecimal.ZERO) < 0 ? "DEBITO" : "CREDITO";
        return build(fecha, descripcion, monto.abs(), tipo);
    }

    private String construirDescripcion(String[] fila, int colDesc, int colRef) {
        String desc = campo(fila, colDesc);
        String ref = campo(fila, colRef);
        if (ref != null && !ref.isBlank() && desc != null && !desc.isBlank()) return ref + " — " + desc;
        if (ref != null && !ref.isBlank()) return ref;
        return desc != null ? desc : "";
    }

    private String campo(String[] fila, int indice) {
        if (indice < 0 || indice >= fila.length) return null;
        String valor = fila[indice];
        return valor != null ? valor.trim() : null;
    }

    private boolean filaVacia(String[] fila) {
        for (String celda : fila) {
            if (celda != null && !celda.trim().isEmpty()) return false;
        }
        return true;
    }

    private Movimiento build(LocalDate fecha, String descripcion, BigDecimal monto, String tipo) {
        return Movimiento.builder()
                .fecha(fecha).descripcion(descripcion).monto(monto).tipo(tipo)
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }

    private Charset resolverCharset(String encoding) {
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private char resolverSeparador(String separador) {
        if (separador == null || separador.isEmpty()) return ',';
        if (separador.equals("\\t")) return '\t';
        return separador.charAt(0);
    }
}
