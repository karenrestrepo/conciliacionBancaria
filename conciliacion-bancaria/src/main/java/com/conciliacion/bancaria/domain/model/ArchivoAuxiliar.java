package com.conciliacion.bancaria.domain.model;

/**
 * Un auxiliar abierto: o es una tabla (basta con saber qué hay en cada columna) o es un
 * documento que la IA debe leer completo. Solo uno de los dos viene lleno.
 */
public record ArchivoAuxiliar(TablaArchivo tabla, Documento documento) {

    public enum Tipo { PDF, JPG, PNG, TEXTO }

    /** @param datos bytes originales para PDF e imágenes; para TEXTO, el texto en UTF-8 */
    public record Documento(Tipo tipo, byte[] datos) {}

    public static ArchivoAuxiliar deTabla(TablaArchivo tabla) {
        return new ArchivoAuxiliar(tabla, null);
    }

    public static ArchivoAuxiliar deDocumento(Tipo tipo, byte[] datos) {
        return new ArchivoAuxiliar(null, new Documento(tipo, datos));
    }

    public boolean esTabla() {
        return tabla != null;
    }
}
