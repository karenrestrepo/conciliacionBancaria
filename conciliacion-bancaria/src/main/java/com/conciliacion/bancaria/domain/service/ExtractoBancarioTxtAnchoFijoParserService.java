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
 * Parser de extractos bancarios TXT de ancho fijo.
 *
 * Modo por defecto (Davivienda corriente):
 *   "DD   MM  <Descripción>    <Oficina>    <Doc>    <Valor>+/-    <Saldo>+/-"
 *
 * Modo con patrón personalizado (campo "patronLinea" en configuracionDetalle):
 *   Regex Java con grupos nombrados:
 *     (?<fecha>...)      → fecha completa, parseada con "formatoFechaLinea" (ej: dd/MM/yyyy)
 *     (?<dia>...)        → día, combinado con (?<mes>...) y el año del período
 *     (?<descripcion>...) → texto de la transacción
 *     (?<monto>...)      → número sin signo
 *     (?<signo>...)      → '+' o '-'  (alternativa: (?<tipo>...) con CREDITO/DEBITO/C/D)
 *   Grupos mínimos requeridos: (fecha o dia+mes) + descripcion + monto
 */
@Slf4j
public class ExtractoBancarioTxtAnchoFijoParserService {

    /** Patrón por defecto: Davivienda cuenta corriente / ahorro */
    private static final Pattern PATRON_DAVIVIENDA = Pattern.compile(
            "^\\s*(\\d{2})\\s+(\\d{2})\\s+(.*?)\\s+\\$?([\\d.,]+)([+\\-])\\s+\\$?[\\d.,]+[+\\-]\\s*$");

    public List<Movimiento> parsear(byte[] contenido, Map<String, Object> config, String periodo) {

        String encoding        = getString(config, "encoding", "ISO-8859-1");
        String sepMiles        = getString(config, "separadorMiles", ",");
        String sepDecimales    = getString(config, "separadorDecimales", ".");
        BigDecimal factorMonto = getBigDecimal(config, "factorMonto", BigDecimal.ONE);
        String patronPersonal  = getString(config, "patronLinea", null);
        String fmtFecha        = getString(config, "formatoFechaLinea", "dd/MM/yyyy");
        boolean invertirSigno  = Boolean.TRUE.equals(config.get("invertirSigno"));
        int anio = extractYear(periodo);

        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (Exception e) {
            charset = Charset.forName("ISO-8859-1");
        }

        Pattern patron;
        boolean modoPersonal;
        if (patronPersonal != null && !patronPersonal.isBlank()) {
            try {
                patron = Pattern.compile(patronPersonal);
                modoPersonal = true;
                log.info("Usando patrón personalizado de línea: {}", patronPersonal);
            } catch (Exception e) {
                throw new CsvValidationException(
                        "El patrón de línea configurado no es un regex válido: " + e.getMessage());
            }
        } else {
            patron = PATRON_DAVIVIENDA;
            modoPersonal = false;
        }

        List<Movimiento> movimientos = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(contenido), charset))) {

            String linea;
            while ((linea = reader.readLine()) != null) {
                Matcher m = patron.matcher(linea);
                if (!m.find()) continue;

                Movimiento mov;
                if (modoPersonal) {
                    mov = parsearConPatronPersonal(m, anio, fmtFecha, sepMiles, sepDecimales, factorMonto, invertirSigno, linea);
                } else {
                    mov = parsearDavivienda(m, anio, sepMiles, sepDecimales, factorMonto, linea);
                }
                if (mov != null) movimientos.add(mov);
            }
        } catch (IOException e) {
            throw new CsvValidationException("Error al leer el extracto: " + e.getMessage());
        }

        if (movimientos.isEmpty()) {
            throw new CsvValidationException(
                    "El extracto no contiene movimientos válidos. "
                    + "Verifique el patrón de línea configurado y el formato del archivo.");
        }
        return movimientos;
    }

    // ── Parseo modo Davivienda (patrón por defecto) ───────────────────────────

    private Movimiento parsearDavivienda(Matcher m, int anio,
                                          String sepMiles, String sepDecimales,
                                          BigDecimal factorMonto, String linea) {
        int dia = Integer.parseInt(m.group(1));
        int mes = Integer.parseInt(m.group(2));
        LocalDate fecha;
        try {
            fecha = LocalDate.of(anio, mes, dia);
        } catch (DateTimeException e) {
            log.debug("Línea ignorada por fecha inválida (día={}, mes={}): {}", dia, mes, linea);
            return null;
        }
        BigDecimal monto = parsearMonto(m.group(4), sepMiles, sepDecimales).multiply(factorMonto);
        if (monto.compareTo(BigDecimal.ZERO) == 0) return null;
        String descripcion = construirDescripcion(m.group(3));
        String tipo = "+".equals(m.group(5)) ? "CREDITO" : "DEBITO";
        return Movimiento.builder().fecha(fecha).descripcion(descripcion)
                .monto(monto).tipo(tipo).estado(EstadoMovimiento.PENDIENTE).build();
    }

    // ── Parseo modo patrón personalizado (grupos nombrados) ───────────────────

    private Movimiento parsearConPatronPersonal(Matcher m, int anio, String fmtFecha,
                                                 String sepMiles, String sepDecimales,
                                                 BigDecimal factorMonto, boolean invertirSigno, String linea) {
        LocalDate fecha = extraerFecha(m, anio, fmtFecha, linea);
        if (fecha == null) return null;

        String descGrupo = grupoOpcional(m, "descripcion");
        if (descGrupo == null) descGrupo = linea.trim();
        String descripcion = descGrupo.trim();

        String montoStr = grupoOpcional(m, "monto");
        if (montoStr == null) return null;
        BigDecimal monto = parsearMonto(montoStr, sepMiles, sepDecimales).multiply(factorMonto);
        if (monto.compareTo(BigDecimal.ZERO) == 0) return null;

        String tipo = resolverTipo(m, invertirSigno);

        return Movimiento.builder().fecha(fecha).descripcion(descripcion)
                .monto(monto).tipo(tipo).estado(EstadoMovimiento.PENDIENTE).build();
    }

    private LocalDate extraerFecha(Matcher m, int anio, String fmtFecha, String linea) {
        // Opción A: grupo "fecha" completo
        String fechaStr = grupoOpcional(m, "fecha");
        if (fechaStr != null && !fechaStr.isBlank()) {
            try {
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern(fmtFecha);
                return LocalDate.parse(fechaStr.trim(), fmt);
            } catch (Exception e) {
                // intentar siguiente opción
            }
        }
        // Opción B: grupos "dia" + "mes" (año del período)
        String diaStr = grupoOpcional(m, "dia");
        String mesStr = grupoOpcional(m, "mes");
        if (diaStr != null && mesStr != null) {
            try {
                return LocalDate.of(anio, Integer.parseInt(mesStr.trim()), Integer.parseInt(diaStr.trim()));
            } catch (Exception e) {
                log.debug("Fecha inválida en línea: {}", linea);
            }
        }
        return null;
    }

    private String resolverTipo(Matcher m, boolean invertirSigno) {
        String signo = grupoOpcional(m, "signo");
        if (signo != null) {
            boolean esCredito = "+".equals(signo.trim());
            if (invertirSigno) esCredito = !esCredito;
            return esCredito ? "CREDITO" : "DEBITO";
        }
        String tipo = grupoOpcional(m, "tipo");
        if (tipo != null) {
            String t = tipo.trim().toUpperCase();
            boolean esCredito = t.startsWith("C");
            if (invertirSigno) esCredito = !esCredito;
            return esCredito ? "CREDITO" : "DEBITO";
        }
        return "DEBITO";
    }

    private String grupoOpcional(Matcher m, String nombre) {
        try { return m.group(nombre); } catch (IllegalArgumentException e) { return null; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String construirDescripcion(String resto) {
        String[] partes = resto.trim().split("\\s{2,}");
        return partes.length > 0 && !partes[0].isBlank() ? partes[0].trim() : resto.trim();
    }

    private BigDecimal parsearMonto(String texto, String sepMiles, String sepDecimales) {
        try {
            String t = texto.trim().replaceAll("[^\\d.,]", "");
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
        try { return new BigDecimal(v.toString()); }
        catch (NumberFormatException e) { return def; }
    }
}
