package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfigExcel;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.service.parser.support.ContinuacionLineaHelper;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.NumberToTextConverter;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser de extractos en Excel (XLS/XLSX), evolución de
 * {@code ExtractoBancarioXlsxParserService} con soporte de continuación de
 * línea y validación de cuadre. Las filas que no traen fecha reconocible
 * (encabezados de tabla repetidos por paginación, filas de relleno, bloques
 * de resumen intercalados) se descartan silenciosamente, salvo que califiquen
 * como continuación de la descripción del movimiento anterior.
 */
@RequiredArgsConstructor
public class ExcelBankStatementParser {

    private final MontoParser montoParser;
    private final FechaResolver fechaResolver;
    private final ContinuacionLineaHelper continuacionHelper;
    private final CuadreValidator cuadreValidator;

    public List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo) {
        ConfigExcel c = config.getExcel();
        int anio = fechaResolver.extraerAnio(periodo);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            Sheet hoja = wb.getSheetAt(c.getNumeroHoja());
            if (hoja == null) {
                throw new CsvValidationException("El archivo no contiene la hoja " + c.getNumeroHoja());
            }

            List<Movimiento> movimientos = new ArrayList<>();
            int totalFilas = hoja.getLastRowNum();

            for (int i = c.getFilasASaltar(); i <= totalFilas; i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                Map<String, String> valores = extraerValores(fila, c);
                LocalDate fecha = resolverFecha(fila, c, valores, anio);

                if (fecha == null) {
                    intentarContinuacion(movimientos, valores, config);
                    continue;
                }

                String descripcion = construirDescripcion(valores);

                if (c.isDebitoYCreditoSeparados()) {
                    BigDecimal debito = montoParser.parsear(valores.get("debito"),
                            config.getSeparadorMiles(), config.getSeparadorDecimales(), config.getFactorMonto());
                    BigDecimal credito = montoParser.parsear(valores.get("credito"),
                            config.getSeparadorMiles(), config.getSeparadorDecimales(), config.getFactorMonto());
                    if (debito.compareTo(BigDecimal.ZERO) == 0 && credito.compareTo(BigDecimal.ZERO) == 0) continue;
                    movimientos.add(debito.compareTo(BigDecimal.ZERO) > 0
                            ? build(fecha, descripcion, debito, "DEBITO")
                            : build(fecha, descripcion, credito, "CREDITO"));
                } else {
                    BigDecimal monto = montoParser.parsear(valores.get("monto"),
                            config.getSeparadorMiles(), config.getSeparadorDecimales(), config.getFactorMonto());
                    if (monto.compareTo(BigDecimal.ZERO) == 0) continue;
                    String tipo = monto.compareTo(BigDecimal.ZERO) < 0 ? "DEBITO" : "CREDITO";
                    movimientos.add(build(fecha, descripcion, monto.abs(), tipo));
                }
            }

            return movimientos;
        } catch (CsvValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvValidationException("Error al leer el extracto: " + e.getMessage());
        }
    }

    public ResultadoCuadre validarCuadre(byte[] contenido, ConfiguracionExtractoDetalle config) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            ConfigExcel c = config.getExcel();
            Sheet hoja = wb.getSheetAt(c.getNumeroHoja());
            if (hoja == null) return ResultadoCuadre.deshabilitado();

            List<List<String>> filas = new ArrayList<>();
            for (Row fila : hoja) {
                List<String> celdas = new ArrayList<>();
                short ultimaCelda = fila.getLastCellNum();
                for (int col = 0; col < ultimaCelda; col++) {
                    celdas.add(leerTexto(fila, col));
                }
                filas.add(celdas);
            }

            return cuadreValidator.validarEnCeldas(filas, config.getCuadre(),
                    config.getSeparadorMiles(), config.getSeparadorDecimales());
        } catch (Exception e) {
            throw new CsvValidationException("Error al leer el extracto para validar cuadre: " + e.getMessage());
        }
    }

    // ── Extracción ──────────────────────────────────────────────────────────

    private Map<String, String> extraerValores(Row fila, ConfigExcel c) {
        Map<String, String> valores = new HashMap<>();
        valores.put("fecha", leerTexto(fila, c.getColumnaFecha()));
        valores.put("descripcion", leerTexto(fila, c.getColumnaDescripcion()));
        if (c.getColumnaReferencia() >= 0) valores.put("referencia", leerTexto(fila, c.getColumnaReferencia()));
        if (c.isDebitoYCreditoSeparados()) {
            valores.put("debito", leerTexto(fila, c.getColumnaDebito()));
            valores.put("credito", leerTexto(fila, c.getColumnaCredito()));
        } else {
            valores.put("monto", leerTexto(fila, c.getColumnaMonto()));
        }
        return valores;
    }

    private LocalDate resolverFecha(Row fila, ConfigExcel c, Map<String, String> valores, int anio) {
        Cell celdaFecha = fila.getCell(c.getColumnaFecha());
        if (celdaFecha != null && celdaFecha.getCellType() == CellType.NUMERIC
                && DateUtil.isCellDateFormatted(celdaFecha)) {
            return celdaFecha.getLocalDateTimeCellValue().toLocalDate();
        }
        String textoFecha = valores.get("fecha");
        if (textoFecha == null || textoFecha.isBlank()) return null;
        return fechaResolver.resolverConFormato(textoFecha, c.getFormatoFecha(), anio);
    }

    private void intentarContinuacion(List<Movimiento> movimientos, Map<String, String> valores,
                                       ConfiguracionExtractoDetalle config) {
        if (movimientos.isEmpty()) return;
        if (!continuacionHelper.esContinuacion(valores, config.getContinuacion())) return;

        String campoDestino = config.getContinuacion().getCampoDestino();
        if (!"descripcion".equals(campoDestino)) return;

        String extra = valores.getOrDefault("descripcion", "");
        if (extra == null || extra.isBlank()) return;

        int idx = movimientos.size() - 1;
        Movimiento anterior = movimientos.get(idx);
        String nuevaDescripcion = (anterior.getDescripcion() + " " + extra.trim()).trim();
        movimientos.set(idx, anterior.withDescripcion(nuevaDescripcion));
    }

    private String construirDescripcion(Map<String, String> valores) {
        String desc = valores.get("descripcion");
        String ref = valores.get("referencia");
        if (ref != null && !ref.isBlank() && desc != null && !desc.isBlank()) return ref + " — " + desc;
        if (ref != null && !ref.isBlank()) return ref;
        return desc != null ? desc : "";
    }

    private String leerTexto(Row fila, int col) {
        if (col < 0) return null;
        Cell celda = fila.getCell(col);
        if (celda == null || celda.getCellType() == CellType.BLANK) return null;
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue().trim();
            case NUMERIC -> NumberToTextConverter.toText(celda.getNumericCellValue());
            default -> null;
        };
    }

    private Movimiento build(LocalDate fecha, String descripcion, BigDecimal monto, String tipo) {
        return Movimiento.builder()
                .fecha(fecha).descripcion(descripcion).monto(monto).tipo(tipo)
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }
}
