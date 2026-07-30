package com.conciliacion.bancaria.domain.service.parser.support;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Resolución de fechas con inferencia de año desde el período de la conciliación
 * — unifica lógica que antes solo vivía en el parser XLSX (normalización de
 * fecha sin año, padding de día/mes), aplicada ahora también a ancho fijo.
 */
public class FechaResolver {

    private static final List<DateTimeFormatter> FORMATOS_ALTERNATIVOS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    );

    public int extraerAnio(String periodo) {
        if (periodo != null && periodo.length() >= 4) {
            try {
                return Integer.parseInt(periodo.substring(0, 4));
            } catch (NumberFormatException ignored) { }
        }
        return LocalDate.now().getYear();
    }

    /** Resuelve una fecha a partir de día y mes por separado, tomando el año del período. */
    public LocalDate resolverDiaMes(String diaTexto, String mesTexto, int anioPeriodo) {
        if (diaTexto == null || mesTexto == null) return null;
        try {
            int dia = Integer.parseInt(diaTexto.trim());
            int mes = Integer.parseInt(mesTexto.trim());
            return LocalDate.of(anioPeriodo, mes, dia);
        } catch (Exception e) {
            return null;
        }
    }

    /** Resuelve una fecha completa con el formato dado, añadiendo el año del período si el formato no lo incluye. */
    public LocalDate resolverConFormato(String texto, String formato, int anioPeriodo) {
        if (texto == null || texto.isBlank()) return null;
        String normalizado = normalizar(texto, formato, anioPeriodo);
        String formatoFinal = asegurarAnio(formato);

        try {
            return LocalDate.parse(normalizado, DateTimeFormatter.ofPattern(formatoFinal));
        } catch (DateTimeParseException e) {
            for (DateTimeFormatter alt : FORMATOS_ALTERNATIVOS) {
                try {
                    return LocalDate.parse(normalizado, alt);
                } catch (DateTimeParseException ignored) { }
            }
            return null;
        }
    }

    private String normalizar(String texto, String formato, int anio) {
        String t = texto.trim();
        char sep = t.contains("/") ? '/' : t.contains("-") ? '-' : 0;
        if (sep == 0) return t;

        String[] partes = t.split(String.valueOf(sep));
        if (partes.length >= 1 && partes[0].length() == 1) partes[0] = "0" + partes[0];
        if (partes.length >= 2 && partes[1].length() == 1) partes[1] = "0" + partes[1];
        t = String.join(String.valueOf(sep), partes);

        boolean formatoSinAnio = !formato.toLowerCase().contains("y");
        if (formatoSinAnio) t = t + sep + anio;
        return t;
    }

    private String asegurarAnio(String formato) {
        if (!formato.toLowerCase().contains("y")) {
            char sep = formato.contains("/") ? '/' : formato.contains("-") ? '-' : '/';
            return formato + sep + "yyyy";
        }
        return formato;
    }
}
