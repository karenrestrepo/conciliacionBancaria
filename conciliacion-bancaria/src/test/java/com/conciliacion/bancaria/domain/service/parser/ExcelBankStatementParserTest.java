package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfigExcel;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.TipoOrigenExtracto;
import com.conciliacion.bancaria.domain.service.parser.support.ContinuacionLineaHelper;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelBankStatementParser — evoluciona ExtractoBancarioXlsxParserService")
class ExcelBankStatementParserTest {

    private ExcelBankStatementParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExcelBankStatementParser(
                new MontoParser(), new FechaResolver(), new ContinuacionLineaHelper(),
                new CuadreValidator(new MontoParser()));
    }

    private void fila(Sheet hoja, int indice, String... valores) {
        Row fila = hoja.createRow(indice);
        for (int c = 0; c < valores.length; c++) {
            if (valores[c] != null) fila.createCell(c).setCellValue(valores[c]);
        }
    }

    private ConfiguracionExtractoDetalle configBasica() {
        ConfigExcel excel = new ConfigExcel();
        excel.setNumeroHoja(0);
        excel.setFilasASaltar(0);
        excel.setColumnaFecha(0);
        excel.setFormatoFecha("d/MM");
        excel.setColumnaDescripcion(1);
        excel.setColumnaMonto(4);

        ConfiguracionExtractoDetalle config = new ConfiguracionExtractoDetalle();
        config.setTipoOrigen(TipoOrigenExtracto.EXCEL);
        config.setSeparadorMiles(",");
        config.setSeparadorDecimales(".");
        config.setExcel(excel);
        return config;
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("bloques de encabezado repetidos y filas vacias intercaladas se descartan, fecha sin anio se infiere del periodo")
        void bloquesDeEncabezadoSeDescartan() throws IOException {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet hoja = wb.createSheet("Extracto");

                fila(hoja, 0, "Información Cliente:");
                fila(hoja, 1, "CLIENTE", "DIRECCIÓN");
                fila(hoja, 2, "PROINDUSTAR S.A.S.", "CL 51 6 106");
                // fila 3 vacía (null row)
                fila(hoja, 4, "Movimientos:");
                fila(hoja, 5, "FECHA", "DESCRIPCIÓN", "SUCURSAL", "DCTO.", "VALOR", "SALDO");
                fila(hoja, 6, "1/06", "CONSIG LOCAL EFECTIVO", "CANAL CORRESPONSA", null, "730,400.00", "1,050,295,846.68");
                fila(hoja, 7, "1/06", "CONSIG LOCAL EFECTIVO", "CANAL CORRESPONSA", null, "-78,000.00", "972,295,846.68");
                // fila 8 vacía
                // Bloque de encabezado repetido por paginación (fila 9-10), como en el Excel real de muestra
                fila(hoja, 9, "Información Cliente:");
                fila(hoja, 10, "FECHA", "DESCRIPCIÓN", "SUCURSAL", "DCTO.", "VALOR", "SALDO");
                fila(hoja, 11, "2/06", "OTRA CONSIGNACION", null, null, "100,000.00", "1,072,295,846.68");
                fila(hoja, 12, null, "FIN ESTADO DE CUENTA");

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);
                byte[] contenido = out.toByteArray();

                List<Movimiento> resultado = parser.parsear(contenido, configBasica(), "2026-06");

                assertThat(resultado).hasSize(3);
                assertThat(resultado.get(0).getFecha()).isEqualTo(LocalDate.of(2026, 6, 1));
                assertThat(resultado.get(0).getTipo()).isEqualTo("CREDITO");
                assertThat(resultado.get(1).getTipo()).isEqualTo("DEBITO");
                assertThat(resultado.get(2).getFecha()).isEqualTo(LocalDate.of(2026, 6, 2));
            }
        }
    }

    @Nested
    @DisplayName("Extractos sin movimientos")
    class SinMovimientos {

        @Test
        @DisplayName("hoja sin filas de datos retorna lista vacia sin lanzar excepcion")
        void hojaVaciaNoLanzaExcepcion() throws IOException {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet hoja = wb.createSheet("Extracto");
                fila(hoja, 0, "FECHA", "DESCRIPCIÓN", "SUCURSAL", "DCTO.", "VALOR", "SALDO");

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);

                List<Movimiento> resultado = parser.parsear(out.toByteArray(), configBasica(), "2026-06");

                assertThat(resultado).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Cuadre")
    class Cuadre {

        @Test
        @DisplayName("etiqueta en fila de encabezado y valor en la misma columna, fila siguiente")
        void cuadreEncabezadoYValorFilaSiguiente() throws IOException {
            try (Workbook wb = new XSSFWorkbook()) {
                Sheet hoja = wb.createSheet("Extracto");
                fila(hoja, 0, "SALDO ANTERIOR", "TOTAL ABONOS", "TOTAL CARGOS", "SALDO ACTUAL");
                fila(hoja, 1, "1,049,565,446.68", "532,350,631.00", "1,099,262,464.49", "482,653,613.19");

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);

                ConfiguracionExtractoDetalle config = configBasica();
                ReglaCuadre regla = new ReglaCuadre();
                regla.setHabilitada(true);
                regla.setEtiquetaSaldoAnterior("SALDO ANTERIOR");
                regla.setEtiquetaCreditos("TOTAL ABONOS");
                regla.setEtiquetaDebitos("TOTAL CARGOS");
                regla.setEtiquetaSaldoFinal("SALDO ACTUAL");
                config.setCuadre(regla);

                assertThat(parser.validarCuadre(out.toByteArray(), config).isCuadra()).isTrue();
            }
        }
    }
}
