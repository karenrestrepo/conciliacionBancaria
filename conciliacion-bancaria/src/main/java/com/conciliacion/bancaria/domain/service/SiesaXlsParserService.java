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

/**
 * Parser del auxiliar de movimientos generado por SIESA (formato .xls / .xlsx).
 *
 * Estructura esperada del archivo exportado:
 * <pre>
 *   Filas 1-17  : encabezados y metadatos del reporte (se omiten)
 *   Fila 18+    : datos de movimientos
 *
 *   Columna 0   : Fecha
 *   Columna 1   : Documento
 *   Columna 4   : Descripción
 *   Columna 9   : Débitos  → tipo CREDITO (ingreso en cuenta)
 *   Columna 10  : Créditos → tipo DEBITO  (egreso de cuenta)
 * </pre>
 *
 * Filas cuyo valor sea cero en ambas columnas de monto (totales, subtotales,
 * filas de saldo) son ignoradas.
 */
@Slf4j
public class SiesaXlsParserService {

    private static final int FILAS_ENCABEZADO = 17;

    // Índices de columna (0-based)
    private static final int COL_FECHA       = 0;
    private static final int COL_DOCUMENTO   = 1;
    private static final int COL_DESCRIPCION = 4;
    private static final int COL_DEBITOS     = 9;   // → CREDITO
    private static final int COL_CREDITOS    = 10;  // → DEBITO

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    public List<Movimiento> parsear(byte[] contenido) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {

            Sheet hoja = wb.getSheetAt(0);
            if (hoja == null) {
                throw new CsvValidationException(
                        "El archivo XLS no contiene ninguna hoja");
            }

            List<Movimiento> movimientos = new ArrayList<>();
            int totalFilas = hoja.getLastRowNum();

            for (int i = FILAS_ENCABEZADO; i <= totalFilas; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                BigDecimal debitos  = leerMonto(fila, COL_DEBITOS);
                BigDecimal creditos = leerMonto(fila, COL_CREDITOS);

                // Saltar filas de totales / saldos (ambos montos son 0 o vacíos)
                if (debitos.compareTo(BigDecimal.ZERO) == 0
                        && creditos.compareTo(BigDecimal.ZERO) == 0) continue;

                LocalDate fecha = leerFecha(fila, COL_FECHA, i + 1);
                if (fecha == null) continue;   // fila sin fecha → total de sección

                String descripcion = construirDescripcion(fila);
                String documento = leerTexto(fila, COL_DOCUMENTO);

                String tipo;
                BigDecimal monto;
                if (debitos.compareTo(BigDecimal.ZERO) > 0) {
                    tipo  = "CREDITO";
                    monto = debitos;
                } else {
                    tipo  = "DEBITO";
                    monto = creditos;
                }

                movimientos.add(Movimiento.builder()
                        .fecha(fecha)
                        .descripcion(descripcion)
                        .monto(monto)
                        .tipo(tipo)
                        .estado(EstadoMovimiento.PENDIENTE)
                        .numeroComprobante(documento != null && !documento.isBlank() ? documento : null)
                        .build());
            }

            if (movimientos.isEmpty()) {
                throw new CsvValidationException(
                        "El archivo SIESA no contiene movimientos válidos. "
                        + "Verifique que el archivo tenga el formato correcto y filas desde la 18.");
            }

            return movimientos;

        } catch (CsvValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvValidationException(
                    "Error al leer el archivo XLS: " + e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private LocalDate leerFecha(Row fila, int col, int numFila) {
        Cell celda = fila.getCell(col);
        if (celda == null || celda.getCellType() == CellType.BLANK) return null;

        // Celda de fecha numérica (Excel serializa fechas como números)
        if (celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
            return celda.getLocalDateTimeCellValue().toLocalDate();
        }

        // Celda de texto — probar varios formatos
        String texto = celda.toString().trim();
        for (DateTimeFormatter fmt : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(texto, fmt);
            } catch (DateTimeParseException ignored) {
                // continuar con siguiente formato
            }
        }

        log.debug("Fila {}: fecha '{}' no reconocida — fila omitida", numFila, texto);
        return null;
    }

    private BigDecimal leerMonto(Row fila, int col) {
        Cell celda = fila.getCell(col);
        if (celda == null || celda.getCellType() == CellType.BLANK) {
            return BigDecimal.ZERO;
        }
        try {
            if (celda.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(celda.getNumericCellValue());
            }
            String texto = celda.toString().trim()
                    .replace(".", "")   // separador de miles colombiano
                    .replace(",", ".");  // separador decimal
            if (texto.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String construirDescripcion(Row fila) {
        String doc  = leerTexto(fila, COL_DOCUMENTO);
        String desc = leerTexto(fila, COL_DESCRIPCION);

        if (!doc.isEmpty() && !desc.isEmpty()) return doc + " — " + desc;
        if (!doc.isEmpty()) return doc;
        return desc;
    }

    private String leerTexto(Row fila, int col) {
        Cell celda = fila.getCell(col);
        if (celda == null) return "";
        return switch (celda.getCellType()) {
            case STRING  -> celda.getStringCellValue().trim();
            case NUMERIC -> NumberToTextConverter.toText(celda.getNumericCellValue());
            default      -> "";
        };
    }
}
