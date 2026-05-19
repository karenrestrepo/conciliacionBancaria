package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import com.opencsv.CSVReader;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class CsvValidatorService {

    // Columnas obligatorias según TRD RT-01
    private static final List<String> COLUMNAS_REQUERIDAS =
            List.of("fecha", "descripcion", "monto", "tipo_movimiento");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<Movimiento> parsear(byte[] contenido, Map<String, String> mapeoColumnas) {
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(contenido)))) {

            String[] encabezados = reader.readNext();
            if (encabezados == null) {
                throw new CsvValidationException("El archivo CSV está vacío");
            }

            // Normalizar encabezados (trim + lowercase)
            String[] headers = java.util.Arrays.stream(encabezados)
                    .map(h -> h.trim().toLowerCase())
                    .toArray(String[]::new);

            // Validar columnas requeridas usando el mapeo del banco
            Map<String, Integer> indiceColumnas = resolverIndices(headers, mapeoColumnas);

            List<Movimiento> movimientos = new ArrayList<>();
            String[] fila;
            int numeroFila = 2;

            while ((fila = reader.readNext()) != null) {
                if (filaVacia(fila)) continue;
                movimientos.add(parsearFila(fila, indiceColumnas, numeroFila));
                numeroFila++;
            }

            if (movimientos.isEmpty()) {
                throw new CsvValidationException("El archivo CSV no contiene movimientos");
            }

            return movimientos;

        } catch (CsvValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvValidationException("Error al leer el CSV: " + e.getMessage());
        }
    }

    private Map<String, Integer> resolverIndices(String[] headers,
                                                 Map<String, String> mapeo) {
        java.util.HashMap<String, Integer> indices = new java.util.HashMap<>();

        for (String columnaRequerida : COLUMNAS_REQUERIDAS) {
            // El mapeo traduce nombre del banco → nombre estándar
            String nombreEnCsv = mapeo.getOrDefault(columnaRequerida, columnaRequerida)
                    .trim().toLowerCase();

            int idx = IntStream.range(0, headers.length)
                    .filter(i -> headers[i].equals(nombreEnCsv))
                    .findFirst()
                    .orElseThrow(() -> new CsvValidationException(
                            "Columna requerida no encontrada: '" + columnaRequerida
                                    + "' (esperada como '" + nombreEnCsv + "' en el CSV)"));

            indices.put(columnaRequerida, idx);
        }
        return indices;
    }

    private Movimiento parsearFila(String[] fila, Map<String, Integer> indices,
                                   int numeroFila) {
        try {
            LocalDate fecha = LocalDate.parse(
                    fila[indices.get("fecha")].trim(), FORMATTER);

            BigDecimal monto = new BigDecimal(
                    fila[indices.get("monto")].trim().replace(",", "."));

            String tipo = fila[indices.get("tipo_movimiento")].trim().toUpperCase();
            if (!tipo.equals("DEBITO") && !tipo.equals("CREDITO")) {
                throw new CsvValidationException(
                        "Fila " + numeroFila + ": tipo_movimiento inválido '"
                                + tipo + "' (esperado: DEBITO o CREDITO)");
            }

            return Movimiento.builder()
                    .fecha(fecha)
                    .descripcion(fila[indices.get("descripcion")].trim())
                    .monto(monto)
                    .tipo(tipo)
                    .estado(EstadoMovimiento.PENDIENTE)
                    .build();

        } catch (DateTimeParseException e) {
            throw new CsvValidationException(
                    "Fila " + numeroFila + ": fecha inválida, use formato yyyy-MM-dd");
        } catch (NumberFormatException e) {
            throw new CsvValidationException(
                    "Fila " + numeroFila + ": monto inválido, use formato numérico");
        }
    }

    private boolean filaVacia(String[] fila) {
        for (String celda : fila) {
            if (celda != null && !celda.trim().isEmpty()) return false;
        }
        return true;
    }
}