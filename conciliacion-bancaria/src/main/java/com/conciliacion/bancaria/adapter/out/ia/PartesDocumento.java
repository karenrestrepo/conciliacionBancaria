package com.conciliacion.bancaria.adapter.out.ia;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.ArchivoLeido.Documento;
import com.conciliacion.bancaria.domain.model.ArchivoLeido.Tipo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Divide un PDF, una foto o un reporte de texto en partes que la IA puede leer sin saltarse filas. */
final class PartesDocumento {

    /** Pocas páginas por parte: la respuesta no se corta y la IA no se salta movimientos. */
    private static final int PAGINAS_POR_PARTE = 2;
    private static final int LINEAS_POR_PARTE = 120;
    /** Primeras líneas que se repiten en cada parte de texto para que se entiendan las columnas. */
    private static final int LINEAS_DE_REFERENCIA = 15;
    /** Evita un gasto inesperado con un archivo equivocado. */
    private static final int MAX_LINEAS = 12_000;

    /**
     * @param contenido bloque de contenido para la API
     * @param nota      qué fragmento es, para añadir a la indicación
     */
    record Parte(Map<String, Object> contenido, String nota) {}

    private PartesDocumento() {
    }

    static List<Parte> de(Documento documento) {
        return switch (documento.tipo()) {
            case PDF -> dePdf(documento.datos());
            case TEXTO -> deTexto(new String(documento.datos(), StandardCharsets.UTF_8));
            case JPG, PNG -> {
                String mime = documento.tipo() == Tipo.PNG ? "image/png" : "image/jpeg";
                yield List.of(new Parte(adjunto("image", mime, documento.datos()), ""));
            }
        };
    }

    private static List<Parte> dePdf(byte[] pdf) {
        return DivisorPdf.dividir(pdf, PAGINAS_POR_PARTE).stream()
                .map(bloque -> {
                    String nota = "";
                    if (bloque.totalPaginas() > bloque.hasta() - bloque.desde() + 1) {
                        nota = " Es un fragmento: páginas " + bloque.desde() + " a " + bloque.hasta()
                                + " de " + bloque.totalPaginas() + ".";
                    }
                    if (bloque.llevaPaginaDeReferencia()) {
                        nota += " La primera página del fragmento es la página 1 y se incluye solo como"
                                + " referencia (encabezados, período y saldos): NO incluyas sus movimientos.";
                    }
                    return new Parte(adjunto("document", "application/pdf", bloque.pdf()), nota);
                })
                .toList();
    }

    private static List<Parte> deTexto(String texto) {
        List<String> lineas = texto.lines().filter(l -> !l.isBlank()).toList();
        if (lineas.isEmpty()) {
            throw new CsvValidationException("El archivo está vacío");
        }
        if (lineas.size() > MAX_LINEAS) {
            throw new CsvValidationException("El archivo de texto es demasiado largo. Divídalo o expórtelo en Excel");
        }
        List<Parte> partes = new ArrayList<>();
        for (int desde = 0; desde < lineas.size(); desde += LINEAS_POR_PARTE) {
            int hasta = Math.min(desde + LINEAS_POR_PARTE, lineas.size());
            String fragmento = String.join("\n", lineas.subList(desde, hasta));
            if (desde > 0) {
                fragmento = "--- REFERENCIA (no incluir sus movimientos) ---\n"
                        + String.join("\n", lineas.subList(0, LINEAS_DE_REFERENCIA))
                        + "\n--- FRAGMENTO A TRANSCRIBIR ---\n" + fragmento;
            }
            String nota = lineas.size() > LINEAS_POR_PARTE
                    ? " Es un fragmento: líneas " + (desde + 1) + " a " + hasta + " de " + lineas.size() + "."
                    : "";
            partes.add(new Parte(Map.of("type", "text", "text", fragmento), nota));
        }
        return partes;
    }

    private static Map<String, Object> adjunto(String tipo, String mime, byte[] datos) {
        return Map.of("type", tipo, "source", Map.of(
                "type", "base64", "media_type", mime, "data", Base64.getEncoder().encodeToString(datos)));
    }
}
