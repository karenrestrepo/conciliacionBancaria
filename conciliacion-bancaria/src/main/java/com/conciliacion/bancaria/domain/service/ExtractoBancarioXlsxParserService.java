package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser de extractos bancarios en formato XLSX/XLS.
 * Usa la configuración almacenada en ConfiguracionExtracto para determinar
 * qué columnas y filas leer.
 *
 * Normalización de fechas:
 *  - Si el día tiene un solo dígito se le antepone un cero ("1/05" → "01/05").
 *  - Si el formato no incluye año se infiere del periodo de la conciliación.
 *  - Si la última fila no tiene fecha y alguna celda contiene "fin" se ignora.
 */
@Slf4j
public class ExtractoBancarioXlsxParserService {

    private static final List<DateTimeFormatter> FORMATOS_ALTERNATIVOS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    /**
     * @param contenido            bytes del archivo XLSX/XLS
     * @param config               mapa con los valores de ConfiguracionDetalle
     *                             (filasASaltar, columnaFecha, formatoFecha, …)
     * @param periodo              periodo de la conciliación ("yyyy-MM"), usado
     *                             para inferir el año cuando la fecha del extracto
     *                             no lo incluye
     */
    public List<Movimiento> parsear(byte[] contenido,
                                    Map<String, Object> config,
                                    String periodo) {

        int filasASaltar         = getInt(config, "filasASaltar", 0);
        int colFecha             = getInt(config, "columnaFecha", 0);
        int colDesc              = getInt(config, "columnaDescripcion", 1);
        int colRef               = getInt(config, "columnaReferencia", -1);
        boolean separados        = getBool(config, "debitoYCreditoSeparados", false);
        int colMonto             = getInt(config, "columnaMonto", 4);
        int colDebito            = getInt(config, "columnaDebito", -1);
        int colCredito           = getInt(config, "columnaCredito", -1);
        int numeroHoja           = getInt(config, "numeroHoja", 0);
        String formatoFecha      = getString(config, "formatoFecha", "dd/MM/yyyy");
        BigDecimal factorMonto   = getBigDecimal(config, "factorMonto", BigDecimal.ONE);
        String sepMiles          = getString(config, "separadorMiles", ".");
        String sepDecimales      = getString(config, "separadorDecimales", ",");

        int anio = extractYear(periodo);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {

            Sheet hoja = wb.getSheetAt(numeroHoja);
            if (hoja == null) {
                throw new CsvValidationException("El archivo no contiene la hoja " + numeroHoja);
            }

            List<Movimiento> movimientos = new ArrayList<>();
            int totalFilas = hoja.getLastRowNum();

            for (int i = filasASaltar; i <= totalFilas; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                String textoFecha = leerTexto(fila, colFecha);
                if (textoFecha == null || textoFecha.isBlank()) {
                    // Fila sin fecha: puede ser FIN DE EXTRACTO u otra fila de resumen
                    if (esFilaFin(fila)) {
                        log.debug("Fila {} ignorada: texto de fin de extracto detectado", i + 1);
                    }
                    continue;
                }

                LocalDate fecha;
                Cell celdaFecha = fila.getCell(colFecha);
                if (celdaFecha != null
                        && celdaFecha.getCellType() == CellType.NUMERIC
                        && DateUtil.isCellDateFormatted(celdaFecha)) {
                    fecha = celdaFecha.getLocalDateTimeCellValue().toLocalDate();
                } else {
                    fecha = parsearFechaTexto(textoFecha, formatoFecha, anio, i + 1);
                }
                if (fecha == null) continue;

                String descripcion = construirDescripcion(fila, colDesc, colRef);

                if (separados) {
                    BigDecimal debito  = leerMonto(fila, colDebito, sepMiles, sepDecimales).multiply(factorMonto);
                    BigDecimal credito = leerMonto(fila, colCredito, sepMiles, sepDecimales).multiply(factorMonto);
                    if (debito.compareTo(BigDecimal.ZERO) == 0
                            && credito.compareTo(BigDecimal.ZERO) == 0) continue;

                    if (debito.compareTo(BigDecimal.ZERO) > 0) {
                        movimientos.add(build(fecha, descripcion, debito, "DEBITO"));
                    } else {
                        movimientos.add(build(fecha, descripcion, credito, "CREDITO"));
                    }
                } else {
                    BigDecimal monto = leerMonto(fila, colMonto, sepMiles, sepDecimales).multiply(factorMonto);
                    if (monto.compareTo(BigDecimal.ZERO) == 0) continue;
                    String tipo = monto.compareTo(BigDecimal.ZERO) < 0 ? "DEBITO" : "CREDITO";
                    movimientos.add(build(fecha, descripcion, monto.abs(), tipo));
                }
            }

            if (movimientos.isEmpty()) {
                throw new CsvValidationException(
                        "El extracto no contiene movimientos válidos. "
                        + "Verifique la configuración de columnas y filas a saltar.");
            }
            return movimientos;

        } catch (CsvValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvValidationException("Error al leer el extracto: " + e.getMessage());
        }
    }

    // ── Date parsing ─────────────────────────────────────────────────────────

    private LocalDate parsearFechaTexto(String texto, String formato,
                                         int anio, int numFila) {
        String normalizado = normalizarFecha(texto, formato, anio);
        String formatoFinal = asegurarAnioEnFormato(formato);

        try {
            return LocalDate.parse(normalizado, DateTimeFormatter.ofPattern(formatoFinal));
        } catch (DateTimeParseException e) {
            for (DateTimeFormatter alt : FORMATOS_ALTERNATIVOS) {
                try { return LocalDate.parse(normalizado, alt); }
                catch (DateTimeParseException ignored) { }
            }
            throw new CsvValidationException(
                    "Fila " + numFila + ": fecha inválida '" + texto + "'");
        }
    }

    /**
     * Normaliza el texto de fecha:
     * 1. Detecta el separador ("/" o "-").
     * 2. Añade cero delante del día si tiene un solo dígito.
     * 3. Si el formato no incluye año, añade el año del periodo al final.
     */
    private String normalizarFecha(String texto, String formato, int anio) {
        String t = texto.trim();

        char sep = t.contains("/") ? '/' : t.contains("-") ? '-' : 0;
        if (sep == 0) return t;

        String[] partes = t.split(String.valueOf(sep));

        // Pad día (primera parte) con cero si es de un dígito
        if (partes.length >= 1 && partes[0].length() == 1) {
            partes[0] = "0" + partes[0];
        }
        // Pad mes (segunda parte) con cero si es de un dígito
        if (partes.length >= 2 && partes[1].length() == 1) {
            partes[1] = "0" + partes[1];
        }

        t = String.join(String.valueOf(sep), partes);

        // Añadir año si el formato no lo incluye
        boolean formatoSinAnio = !formato.toLowerCase().contains("y");
        if (formatoSinAnio) {
            t = t + sep + anio;
        }

        return t;
    }

    private String asegurarAnioEnFormato(String formato) {
        if (!formato.toLowerCase().contains("y")) {
            char sep = formato.contains("/") ? '/' : formato.contains("-") ? '-' : '/';
            return formato + sep + "yyyy";
        }
        return formato;
    }

    private int extractYear(String periodo) {
        if (periodo != null && periodo.length() >= 4) {
            try {
                return Integer.parseInt(periodo.substring(0, 4));
            } catch (NumberFormatException ignored) { }
        }
        return LocalDate.now().getYear();
    }

    // ── FIN row detection ────────────────────────────────────────────────────

    private boolean esFilaFin(Row fila) {
        for (Cell cell : fila) {
            if (cell.getCellType() == CellType.STRING) {
                String text = cell.getStringCellValue().trim().toLowerCase();
                if (text.contains("fin")) return true;
            }
        }
        return false;
    }

    // ── Cell readers ─────────────────────────────────────────────────────────

    private String leerTexto(Row fila, int col) {
        if (col < 0) return null;
        Cell celda = fila.getCell(col);
        if (celda == null || celda.getCellType() == CellType.BLANK) return null;
        return switch (celda.getCellType()) {
            case STRING  -> celda.getStringCellValue().trim();
            case NUMERIC -> NumberToTextConverter.toText(celda.getNumericCellValue());
            default      -> null;
        };
    }

    private BigDecimal leerMonto(Row fila, int col, String sepMiles, String sepDecimales) {
        if (col < 0) return BigDecimal.ZERO;
        Cell celda = fila.getCell(col);
        if (celda == null || celda.getCellType() == CellType.BLANK) return BigDecimal.ZERO;
        try {
            if (celda.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(celda.getNumericCellValue());
            }
            String texto = celda.toString().trim();
            if (!sepMiles.isEmpty()) texto = texto.replace(sepMiles, "");
            if (!sepDecimales.equals(".")) texto = texto.replace(sepDecimales, ".");
            if (texto.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String construirDescripcion(Row fila, int colDesc, int colRef) {
        String desc = leerTexto(fila, colDesc);
        if (colRef < 0) return desc != null ? desc : "";
        String ref  = leerTexto(fila, colRef);
        if (ref != null && !ref.isBlank() && desc != null && !desc.isBlank()) return ref + " — " + desc;
        if (ref != null && !ref.isBlank()) return ref;
        return desc != null ? desc : "";
    }

    private Movimiento build(LocalDate fecha, String descripcion,
                              BigDecimal monto, String tipo) {
        return Movimiento.builder()
                .fecha(fecha)
                .descripcion(descripcion)
                .monto(monto)
                .tipo(tipo)
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }

    // ── Config helpers ───────────────────────────────────────────────────────

    private int getInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return def; }
    }

    private boolean getBool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }

    private String getString(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
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
