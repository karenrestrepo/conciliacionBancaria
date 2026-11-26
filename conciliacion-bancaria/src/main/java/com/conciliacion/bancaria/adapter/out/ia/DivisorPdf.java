package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parte un PDF en bloques de pocas páginas para que la IA no se salte movimientos.
 * Cada bloque, desde el segundo, repite la página 1 como referencia (encabezados, período, saldos).
 */
class DivisorPdf {

    /** @param desde primera página a transcribir (empieza en 1) */
    record Bloque(byte[] pdf, int desde, int hasta, int totalPaginas) {
        boolean llevaPaginaDeReferencia() {
            return desde > 1;
        }
    }

    static List<Bloque> dividir(byte[] pdf, int paginasPorBloque) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            int total = documento.getNumberOfPages();
            if (total == 0) {
                throw new CsvValidationException("El PDF no tiene páginas");
            }
            if (total <= paginasPorBloque && !documento.isEncrypted()) {
                return List.of(new Bloque(pdf, 1, total, total));
            }
            // Un PDF con restricciones pero sin clave de apertura se puede leer: se guarda sin ellas.
            documento.setAllSecurityToBeRemoved(true);
            List<Bloque> bloques = new ArrayList<>();
            for (int desde = 1; desde <= total; desde += paginasPorBloque) {
                int hasta = Math.min(desde + paginasPorBloque - 1, total);
                bloques.add(new Bloque(extraer(documento, desde, hasta), desde, hasta, total));
            }
            return bloques;
        } catch (InvalidPasswordException e) {
            throw new CsvValidationException(
                    "El PDF está protegido con contraseña. Guarde una copia sin contraseña e intente de nuevo");
        } catch (IOException e) {
            throw new CsvValidationException("No se pudo abrir el PDF. Verifique que el archivo no esté dañado");
        }
    }

    private static byte[] extraer(PDDocument origen, int desde, int hasta) throws IOException {
        try (PDDocument parte = new PDDocument()) {
            if (desde > 1) {
                parte.importPage(origen.getPage(0));
            }
            for (int pagina = desde; pagina <= hasta; pagina++) {
                parte.importPage(origen.getPage(pagina - 1));
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            parte.save(salida);
            return salida.toByteArray();
        }
    }
}
