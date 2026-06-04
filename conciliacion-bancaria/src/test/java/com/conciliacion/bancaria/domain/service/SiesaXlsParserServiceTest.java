package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SiesaXlsParserService — lectura de formato SIESA")
class SiesaXlsParserServiceTest {

    private SiesaXlsParserService parser;

    @BeforeEach
    void setUp() {
        parser = new SiesaXlsParserService();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Crea un XLS en memoria con 17 filas de encabezado vacías y luego
     * las filas de movimientos indicadas.
     *
     * @param filas varargs: {fecha (dd/MM/yyyy), doc, desc, debito, credito}
     */
    private byte[] xlsConMovimientos(Object[]... filas) throws IOException {
        try (Workbook wb = new HSSFWorkbook()) {
            Sheet hoja = wb.createSheet("Movimientos");

            // 17 filas de encabezado (vacías)
            for (int i = 0; i < 17; i++) {
                hoja.createRow(i);
            }

            // Filas de datos — coincide con layout SIESA
            for (Object[] datos : filas) {
                int rowIdx = hoja.getLastRowNum() + 1;
                Row fila = hoja.createRow(rowIdx);

                // col 0: fecha (string)
                if (datos[0] != null) fila.createCell(0).setCellValue((String) datos[0]);
                // col 1: documento
                if (datos[1] != null) fila.createCell(1).setCellValue((String) datos[1]);
                // col 4: descripción
                if (datos[2] != null) fila.createCell(4).setCellValue((String) datos[2]);
                // col 9: débitos (→ CREDITO)
                fila.createCell(9).setCellValue(((Number) datos[3]).doubleValue());
                // col 10: créditos (→ DEBITO)
                fila.createCell(10).setCellValue(((Number) datos[4]).doubleValue());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("un débito genera movimiento CREDITO")
        void debitoGeneraCREDITO() throws IOException {
            byte[] xls = xlsConMovimientos(
                    new Object[]{"15/01/2025", "NI-001", "Pago cliente X", 500_000.0, 0.0}
            );

            List<Movimiento> resultado = parser.parsear(xls);

            assertThat(resultado).hasSize(1);
            Movimiento m = resultado.get(0);
            assertThat(m.getTipo()).isEqualTo("CREDITO");
            assertThat(m.getMonto()).isEqualByComparingTo(new BigDecimal("500000.0"));
            assertThat(m.getFecha()).isEqualTo(LocalDate.of(2025, 1, 15));
            assertThat(m.getDescripcion()).contains("NI-001").contains("Pago cliente X");
        }

        @Test
        @DisplayName("un crédito genera movimiento DEBITO")
        void creditoGeneraDEBITO() throws IOException {
            byte[] xls = xlsConMovimientos(
                    new Object[]{"20/02/2025", "EG-002", "Pago proveedor Y", 0.0, 200_000.0}
            );

            List<Movimiento> resultado = parser.parsear(xls);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getTipo()).isEqualTo("DEBITO");
            assertThat(resultado.get(0).getMonto())
                    .isEqualByComparingTo(new BigDecimal("200000.0"));
        }

        @Test
        @DisplayName("filas con ambos montos en cero son ignoradas (totales/saldos)")
        void filasCeroSonIgnoradas() throws IOException {
            byte[] xls = xlsConMovimientos(
                    new Object[]{"01/03/2025", "MOV-001", "Movimiento real", 100_000.0, 0.0},
                    new Object[]{"01/03/2025", null,      "Total cuenta",    0.0,        0.0},
                    new Object[]{"02/03/2025", "MOV-002", "Otro movimiento", 0.0,        50_000.0}
            );

            List<Movimiento> resultado = parser.parsear(xls);

            assertThat(resultado).hasSize(2);
        }

        @Test
        @DisplayName("descripcion solo con documento cuando no hay texto en col 4")
        void descripcionSoloDocumento() throws IOException {
            byte[] xls = xlsConMovimientos(
                    new Object[]{"05/04/2025", "NI-999", null, 300_000.0, 0.0}
            );

            List<Movimiento> resultado = parser.parsear(xls);

            assertThat(resultado.get(0).getDescripcion()).isEqualTo("NI-999");
        }
    }

    @Nested
    @DisplayName("Errores de validación")
    class ErroresValidacion {

        @Test
        @DisplayName("archivo vacío (sin hojas) lanza CsvValidationException")
        void archivoVacioLanzaExcepcion() throws IOException {
            try (Workbook wb = new HSSFWorkbook()) {
                wb.createSheet("vacía");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);

                // Solo encabezados, ningún movimiento real
                assertThatThrownBy(() -> parser.parsear(out.toByteArray()))
                        .isInstanceOf(CsvValidationException.class)
                        .hasMessageContaining("no contiene movimientos válidos");
            }
        }

        @Test
        @DisplayName("fecha inválida en una fila lanza CsvValidationException")
        void fechaInvalidaLanzaExcepcion() throws IOException {
            try (Workbook wb = new HSSFWorkbook()) {
                Sheet hoja = wb.createSheet();
                for (int i = 0; i < 17; i++) hoja.createRow(i);

                Row fila = hoja.createRow(17);
                fila.createCell(0).setCellValue("32/13/2025"); // fecha imposible
                fila.createCell(9).setCellValue(100_000.0);
                fila.createCell(10).setCellValue(0.0);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);

                assertThatThrownBy(() -> parser.parsear(out.toByteArray()))
                        .isInstanceOf(CsvValidationException.class);
            }
        }
    }
}
