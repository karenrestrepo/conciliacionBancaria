package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de extractos bancarios en texto plano de ancho fijo (ej. extracto
 * mensual de Davivienda generado como reporte de impresión).
 *
 * Cada línea de movimiento sigue el patrón:
 *   "DD   MM  <Clase de movimiento>    <Oficina>    <Doc>    <Valor>+/-    <Saldo>+/-"
 * Las líneas que no calzan ese patrón (encabezados de página, pie de página,
 * líneas de continuación de descripción, resúmenes de saldo) se ignoran.
 */
@Slf4j
public class ExtractoBancarioTxtAnchoFijoParserService {

    private static final Pattern PATRON_MOVIMIENTO = Pattern.compile(
            "^\\s*(\\d{2})\\s+(\\d{2})\\s+(.*?)\\s+\\$?([\\d.,]+)([+\\-])\\s+\\$?[\\d.,]+[+\\-]\\s*$");

    public List<Movimiento> parsear(byte[] contenido, Map<String, Object> config, String periodo) {

        String encoding         = getString(config, "encoding", "ISO-8859-1");
        String sepMiles         = getString(config, "separadorMiles", ",");
        String sepDecimales     = getString(config, "separadorDecimales", ".");
        BigDecimal factorMonto  = getBigDecimal(config, "factorMonto", BigDecimal.ONE);
        int anio = extractYear(periodo);

        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (Exception e) {
            charset = Charset.forName("ISO-8859-1");
        }

        List<Movimiento> movimientos = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(contenido), charset))) {

            String linea;
            while ((linea = reader.readLine()) != null) {
                Matcher m = PATRON_MOVIMIENTO.matcher(linea);
                if (!m.matches()) continue;

                int dia = Integer.parseInt(m.group(1));
                int mes = Integer.parseInt(m.group(2));

                LocalDate fecha;
                try {
                    fecha = LocalDate.of(anio, mes, dia);
                } catch (DateTimeException e) {
                    log.debug("Línea ignorada por fecha inválida (día={}, mes={}): {}", dia, mes, linea);
                    continue;
                }

                BigDecimal monto = parsearMonto(m.group(4), sepMiles, sepDecimales).multiply(factorMonto);
                if (monto.compareTo(BigDecimal.ZERO) == 0) continue;

                String descripcion = construirDescripcion(m.group(3));
                String tipo = "+".equals(m.group(5)) ? "CREDITO" : "DEBITO";

                movimientos.add(Movimiento.builder()
                        .fecha(fecha)
                        .descripcion(descripcion)
                        .monto(monto)
                        .tipo(tipo)
                        .estado(EstadoMovimiento.PENDIENTE)
                        .build());
            }
        } catch (IOException e) {
            throw new CsvValidationException("Error al leer el extracto: " + e.getMessage());
        }

        if (movimientos.isEmpty()) {
            throw new CsvValidationException(
                    "El extracto no contiene movimientos válidos. "
                    + "Verifique que el archivo corresponda al formato de ancho fijo configurado.");
        }
        return movimientos;
    }

    /** La "Clase de Movimiento" es el primer bloque de texto antes de 2+ espacios (Oficina/Doc). */
    private String construirDescripcion(String resto) {
        String[] partes = resto.trim().split("\\s{2,}");
        return partes.length > 0 && !partes[0].isBlank() ? partes[0].trim() : resto.trim();
    }

    private BigDecimal parsearMonto(String texto, String sepMiles, String sepDecimales) {
        try {
            String t = texto.trim();
            if (sepMiles != null && !sepMiles.isEmpty()) t = t.replace(sepMiles, "");
            if (sepDecimales != null && !sepDecimales.equals(".")) t = t.replace(sepDecimales, ".");
            if (t.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private int extractYear(String periodo) {
        if (periodo != null && periodo.length() >= 4) {
            try { return Integer.parseInt(periodo.substring(0, 4)); }
            catch (NumberFormatException ignored) { }
        }
        return LocalDate.now().getYear();
    }

    private String getString(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : def;
    }

    private BigDecimal getBigDecimal(Map<String, Object> m, String key, BigDecimal def) {
        Object v = m.get(key);
        if (v == null) return def;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
