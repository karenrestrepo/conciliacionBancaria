package com.conciliacion.bancaria.domain.model.extractoconfig;

/**
 * Layout estructural del extracto — determina qué estrategia de parseo se usa.
 * No confundir con {@link com.conciliacion.bancaria.shared.TipoArchivoExtracto},
 * que es el tipo de archivo físico (CSV/TXT/XLS/XLSX/PDF); un mismo tipo de
 * archivo (ej. TXT) puede ser DELIMITADO o ANCHO_FIJO según el banco.
 */
public enum TipoOrigenExtracto {
    DELIMITADO,
    ANCHO_FIJO,
    EXCEL
}
