package com.conciliacion.bancaria.adapter.out.archivo;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.ArchivoAuxiliar.Tipo;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LectorArchivoAdapter — abre Excel y archivos de texto")
class LectorArchivoAdapterTest {

    private final LectorArchivoAdapter lector = new LectorArchivoAdapter();

    private static byte[] excel(Workbook libro) throws IOException {
        try (libro; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Row fila = libro.createSheet("Aux").createRow(2);
            fila.createCell(0).setCellValue("texto");
            fila.createCell(1).setCellValue(1500.5);
            CellStyle estilo = libro.createCellStyle();
            estilo.setDataFormat((short) 14);
            var fecha = fila.createCell(3);
            fecha.setCellValue(java.sql.Date.valueOf(LocalDate.of(2026, 12, 1)));
            fecha.setCellStyle(estilo);
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private void verificar(byte[] contenido) {
        TablaArchivo.Hoja hoja = lector.leer(contenido).tabla().hojas().get(0);
        assertThat(hoja.nombre()).isEqualTo("Aux");
        assertThat(hoja.celda(2, 0).texto()).isEqualTo("texto");
        assertThat(hoja.celda(2, 1).numero()).isEqualByComparingTo("1500.5");
        assertThat(hoja.celda(2, 2).vacia()).isTrue();
        assertThat(hoja.celda(2, 3).fecha()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(hoja.celda(99, 99).vacia()).isTrue();
    }

    @Test
    @DisplayName("lee .xls y .xlsx conservando números y fechas")
    void leeExcel() throws IOException {
        verificar(excel(new HSSFWorkbook()));
        verificar(excel(new XSSFWorkbook()));
    }

    @Test
    @DisplayName("CSV con punto y coma y comas decimales, en codificación de Windows")
    void csvPuntoYComa() {
        byte[] csv = "Fecha;Descripción;Débito\r\n01/12/2026;\"Pago; varios\";1.500,50\r\n"
                .getBytes(Charset.forName("windows-1252"));

        TablaArchivo.Hoja hoja = lector.leer(csv).tabla().hojas().get(0);

        assertThat(hoja.celda(0, 1).texto()).isEqualTo("Descripción");
        assertThat(hoja.celda(1, 1).texto()).isEqualTo("Pago; varios");
        assertThat(hoja.celda(1, 2).texto()).isEqualTo("1.500,50");
    }

    @Test
    @DisplayName("archivo separado por comas con comillas")
    void csvComas() {
        byte[] csv = "fecha,detalle,valor\n2026-12-01,abono,\"1,500.00\"\n".getBytes(StandardCharsets.UTF_8);

        assertThat(lector.leer(csv).tabla().hojas().get(0).celda(1, 2).texto()).isEqualTo("1,500.00");
    }

    @Test
    @DisplayName("una página web con extensión .xls se lee como tabla, respetando celdas combinadas")
    void tablaHtml() {
        byte[] html = ("<html><table><tr><th colspan=\"2\">Detalle</th><th>D&eacute;bito</th></tr>"
                + "<tr><td>01/12/2026</td><td><b>Pago &amp; abono</b></td><td>1.500,50</td></tr></table></html>")
                .getBytes(StandardCharsets.UTF_8);

        TablaArchivo.Hoja hoja = lector.leer(html).tabla().hojas().get(0);

        assertThat(hoja.celda(0, 2).texto()).isEqualTo("Débito");
        assertThat(hoja.celda(1, 1).texto()).isEqualTo("Pago & abono");
        assertThat(hoja.celda(1, 2).texto()).isEqualTo("1.500,50");
    }

    @Test
    @DisplayName("PDF, fotos y reportes de texto alineados con espacios se entregan como documento")
    void documentos() {
        String reporte = "FECHA        DOC      DEBITO         CREDITO\n"
                + "01/12/2026   RC-1     1,500,000.00   0.00\n"
                + "02/12/2026   CE-1     0.00           2,300,000.00\n";

        assertThat(lector.leer(reporte.getBytes()).documento().tipo()).isEqualTo(Tipo.TEXTO);
        assertThat(lector.leer("%PDF-1.7".getBytes()).documento().tipo()).isEqualTo(Tipo.PDF);
        assertThat(lector.leer(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}).documento().tipo())
                .isEqualTo(Tipo.JPG);
        assertThat(lector.leer(new byte[]{(byte) 0x89, 'P', 'N', 'G'}).documento().tipo()).isEqualTo(Tipo.PNG);
        assertThat(lector.leer("<?xml version=\"1.0\"?><datos/>".getBytes()).esTabla()).isFalse();
    }

    @Test
    @DisplayName("rechaza con un mensaje claro lo que no puede leer")
    void noSoportados() {
        assertThatThrownBy(() -> lector.leer(new byte[]{1, 0, 2, 0}))
                .isInstanceOf(CsvValidationException.class).hasMessageContaining("no reconocido");
        assertThatThrownBy(() -> lector.leer(new byte[0])).hasMessageContaining("vacío");
    }
}
