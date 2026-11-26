package com.conciliacion.bancaria.adapter.out.archivo;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.ArchivoAuxiliar;
import com.conciliacion.bancaria.domain.model.ArchivoAuxiliar.Tipo;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import com.conciliacion.bancaria.domain.model.TablaArchivo.Celda;
import com.conciliacion.bancaria.domain.port.out.LectorArchivoPort;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reconoce el archivo por su contenido. Excel, texto con separadores y tablas HTML se entregan como
 * tabla; PDF, fotos y reportes de texto sin columnas se entregan como documento.
 */
@Component
public class LectorArchivoAdapter implements LectorArchivoPort {

    private static final int MAX_FILAS = 50_000;

    private static final int MAX_BYTES_IMAGEN = 5 * 1024 * 1024;
    private static final Pattern FILA_HTML = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr\\s*>");
    private static final Pattern CELDA_HTML =
            Pattern.compile("(?is)<t[dh]\\b([^>]*)>(.*?)(?=</t[dh]\\s*>|<t[dh]\\b|$)");
    private static final Pattern COLSPAN = Pattern.compile("(?i)colspan\\s*=\\s*[\"']?(\\d{1,2})");
    private static final Pattern ENTIDAD_NUMERICA = Pattern.compile("&#(x?)([0-9a-fA-F]{1,6});");

    @Override
    public ArchivoAuxiliar leer(byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new CsvValidationException("El archivo está vacío");
        }
        if (empiezaCon(contenido, 0xD0, 0xCF, 0x11, 0xE0) || empiezaCon(contenido, 'P', 'K')) {
            return ArchivoAuxiliar.deTabla(leerExcel(contenido));
        }
        if (empiezaCon(contenido, '%', 'P', 'D', 'F')) {
            return ArchivoAuxiliar.deDocumento(Tipo.PDF, contenido);
        }
        if (empiezaCon(contenido, 0xFF, 0xD8, 0xFF)) {
            return imagen(Tipo.JPG, contenido);
        }
        if (empiezaCon(contenido, 0x89, 'P', 'N', 'G')) {
            return imagen(Tipo.PNG, contenido);
        }
        return leerTexto(contenido);
    }

    private ArchivoAuxiliar imagen(Tipo tipo, byte[] contenido) {
        if (contenido.length > MAX_BYTES_IMAGEN) {
            throw new CsvValidationException("La imagen supera 5 MB. Reduzca su tamaño o súbala como PDF");
        }
        return ArchivoAuxiliar.deDocumento(tipo, contenido);
    }

    private boolean empiezaCon(byte[] contenido, int... firma) {
        if (contenido.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) {
            if ((contenido[i] & 0xFF) != firma[i]) return false;
        }
        return true;
    }

    private TablaArchivo leerExcel(byte[] contenido) {
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(contenido))) {
            List<TablaArchivo.Hoja> hojas = new ArrayList<>();
            for (int h = 0; h < libro.getNumberOfSheets(); h++) {
                Sheet hoja = libro.getSheetAt(h);
                validarTamano(hoja.getLastRowNum() + 1);
                List<List<Celda>> filas = new ArrayList<>();
                for (int f = 0; f <= hoja.getLastRowNum(); f++) {
                    Row fila = hoja.getRow(f);
                    List<Celda> celdas = new ArrayList<>();
                    for (int c = 0; fila != null && c < fila.getLastCellNum(); c++) {
                        celdas.add(convertir(fila.getCell(c)));
                    }
                    filas.add(celdas);
                }
                hojas.add(new TablaArchivo.Hoja(hoja.getSheetName(), filas));
            }
            if (hojas.isEmpty()) {
                throw new CsvValidationException("El archivo de Excel no tiene hojas");
            }
            return new TablaArchivo(hojas);
        } catch (CsvValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvValidationException(
                    "No se pudo abrir el archivo de Excel. Verifique que no esté dañado ni tenga contraseña");
        }
    }

    private Celda convertir(Cell celda) {
        if (celda == null) return Celda.VACIA;
        CellType tipo = celda.getCellType() == CellType.FORMULA
                ? celda.getCachedFormulaResultType() : celda.getCellType();
        if (tipo == CellType.STRING) {
            return Celda.deTexto(celda.getStringCellValue());
        }
        if (tipo != CellType.NUMERIC) {
            return Celda.VACIA;
        }
        if (DateUtil.isCellDateFormatted(celda)) {
            var fecha = celda.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return new Celda(fecha.toString(), null, fecha);
        }
        BigDecimal numero = BigDecimal.valueOf(celda.getNumericCellValue()).stripTrailingZeros();
        return new Celda(numero.toPlainString(), numero, null);
    }

    private ArchivoAuxiliar leerTexto(byte[] contenido) {
        String texto = decodificar(contenido);
        if (texto.indexOf('\0') >= 0) {
            throw new CsvValidationException(
                    "Tipo de archivo no reconocido. Súbalo como PDF, Excel, CSV, texto o foto");
        }
        // Muchos programas contables exportan una página web con extensión .xls.
        if (texto.stripLeading().startsWith("<")) {
            List<List<Celda>> filas = filasHtml(texto);
            return filas.isEmpty() ? documentoDeTexto(texto) : tablaDeTexto("html", filas);
        }
        Character separador = separador(texto);
        if (separador == null) {
            return documentoDeTexto(texto);
        }
        var parser = new RFC4180ParserBuilder().withSeparator(separador).build();
        try (CSVReader lector = new CSVReaderBuilder(new StringReader(texto)).withCSVParser(parser).build()) {
            List<List<Celda>> filas = new ArrayList<>();
            String[] fila;
            while ((fila = lector.readNext()) != null) {
                filas.add(Arrays.stream(fila).map(Celda::deTexto).toList());
                validarTamano(filas.size());
            }
            return tablaDeTexto("texto", filas);
        } catch (CsvValidationException e) {
            throw e;
        } catch (Exception e) {
            // Comillas sin cerrar y similares: que la IA lo lea como texto.
            return documentoDeTexto(texto);
        }
    }

    private ArchivoAuxiliar tablaDeTexto(String nombre, List<List<Celda>> filas) {
        return ArchivoAuxiliar.deTabla(new TablaArchivo(List.of(new TablaArchivo.Hoja(nombre, filas))));
    }

    private ArchivoAuxiliar documentoDeTexto(String texto) {
        return ArchivoAuxiliar.deDocumento(Tipo.TEXTO, texto.getBytes(StandardCharsets.UTF_8));
    }

    private List<List<Celda>> filasHtml(String html) {
        List<List<Celda>> filas = new ArrayList<>();
        Matcher fila = FILA_HTML.matcher(html);
        while (fila.find()) {
            List<Celda> celdas = new ArrayList<>();
            Matcher celda = CELDA_HTML.matcher(fila.group(1));
            while (celda.find()) {
                celdas.add(Celda.deTexto(textoPlano(celda.group(2))));
                // Una celda combinada ocupa varias columnas.
                Matcher colspan = COLSPAN.matcher(celda.group(1));
                int ancho = colspan.find() ? Integer.parseInt(colspan.group(1)) : 1;
                for (int i = 1; i < ancho; i++) celdas.add(Celda.VACIA);
            }
            filas.add(celdas);
            validarTamano(filas.size());
        }
        return filas;
    }

    private String textoPlano(String html) {
        String texto = html.replaceAll("(?s)<[^>]*>", " ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"");
        String vocales = "aeiouAEIOU";
        String tildadas = "áéíóúÁÉÍÓÚ";
        for (int i = 0; i < vocales.length(); i++) {
            texto = texto.replace("&" + vocales.charAt(i) + "acute;", String.valueOf(tildadas.charAt(i)));
        }
        texto = texto.replace("&ntilde;", "ñ").replace("&Ntilde;", "Ñ");
        texto = ENTIDAD_NUMERICA.matcher(texto).replaceAll(m -> {
            int codigo = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            return Matcher.quoteReplacement(
                    Character.isValidCodePoint(codigo) ? Character.toString(codigo) : " ");
        });
        return texto.replace("&amp;", "&").replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private void validarTamano(int filas) {
        if (filas > MAX_FILAS) {
            throw new CsvValidationException("El archivo tiene demasiadas filas (máximo " + MAX_FILAS + ")");
        }
    }

    private String decodificar(byte[] contenido) {
        try {
            String texto = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(contenido)).toString();
            return texto.startsWith("﻿") ? texto.substring(1) : texto;
        } catch (CharacterCodingException e) {
            // Muchos programas contables en Windows exportan en esta codificación.
            return new String(contenido, Charset.forName("windows-1252"));
        }
    }

    /**
     * Separador de columnas del texto, o null si no es un archivo separado (por ejemplo un reporte
     * alineado con espacios). La coma se evalúa al final porque también marca miles y decimales.
     */
    private Character separador(String texto) {
        List<String> lineas = texto.lines().filter(l -> !l.isBlank()).limit(50).toList();
        Character mejor = null;
        long mayor = 0;
        for (char candidato : new char[]{';', '\t', '|'}) {
            long conVarios = conVarios(lineas, candidato);
            if (conVarios > mayor && conVarios * 2 >= lineas.size()) {
                mejor = candidato;
                mayor = conVarios;
            }
        }
        if (mejor != null) return mejor;
        long alineadas = lineas.stream().filter(l -> l.strip().contains("   ")).count();
        return conVarios(lineas, ',') * 2 >= lineas.size() && !lineas.isEmpty() && alineadas * 3 < lineas.size()
                ? ',' : null;
    }

    private long conVarios(List<String> lineas, char separador) {
        return lineas.stream()
                .filter(l -> l.chars().filter(c -> c == separador).count() >= 2)
                .count();
    }
}
