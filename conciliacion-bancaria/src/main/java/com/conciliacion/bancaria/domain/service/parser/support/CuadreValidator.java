package com.conciliacion.bancaria.domain.service.parser.support;

import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaCuadre;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida saldoAnterior + créditos - débitos = saldoFinal buscando etiquetas de
 * texto literal (ej. "Saldo Anterior") configuradas por el usuario — sin regex
 * de usuario. El regex interno solo EXTRAE el token numérico crudo; su
 * interpretación (separador de miles/decimal) siempre pasa por {@link MontoParser}
 * usando los separadores configurados para esa configuración de extracto.
 */
public class CuadreValidator {

    private static final Pattern NUMERO = Pattern.compile("[-+]?\\$?[\\d.,]+");

    private final MontoParser montoParser;

    public CuadreValidator(MontoParser montoParser) {
        this.montoParser = montoParser;
    }

    /** Modo texto (TXT/CSV): etiqueta y valor en la misma línea. */
    public ResultadoCuadre validarEnTexto(List<String> lineas, ReglaCuadre regla,
                                           String separadorMiles, String separadorDecimales) {
        if (regla == null || !regla.isHabilitada()) return ResultadoCuadre.deshabilitado();

        BigDecimal saldoAnterior = buscarEnLineas(lineas, regla.getEtiquetaSaldoAnterior(), separadorMiles, separadorDecimales);
        BigDecimal creditos = buscarEnLineas(lineas, regla.getEtiquetaCreditos(), separadorMiles, separadorDecimales);
        BigDecimal debitos = buscarEnLineas(lineas, regla.getEtiquetaDebitos(), separadorMiles, separadorDecimales);
        BigDecimal saldoFinal = buscarEnLineas(lineas, regla.getEtiquetaSaldoFinal(), separadorMiles, separadorDecimales);

        return construir(saldoAnterior, creditos, debitos, saldoFinal, regla.getTolerancia());
    }

    /** Modo celdas (Excel): la etiqueta puede estar en una celda y el valor en la celda adyacente o en la misma columna de la fila siguiente. */
    public ResultadoCuadre validarEnCeldas(List<List<String>> filas, ReglaCuadre regla,
                                            String separadorMiles, String separadorDecimales) {
        if (regla == null || !regla.isHabilitada()) return ResultadoCuadre.deshabilitado();

        BigDecimal saldoAnterior = buscarEnCeldas(filas, regla.getEtiquetaSaldoAnterior(), separadorMiles, separadorDecimales);
        BigDecimal creditos = buscarEnCeldas(filas, regla.getEtiquetaCreditos(), separadorMiles, separadorDecimales);
        BigDecimal debitos = buscarEnCeldas(filas, regla.getEtiquetaDebitos(), separadorMiles, separadorDecimales);
        BigDecimal saldoFinal = buscarEnCeldas(filas, regla.getEtiquetaSaldoFinal(), separadorMiles, separadorDecimales);

        return construir(saldoAnterior, creditos, debitos, saldoFinal, regla.getTolerancia());
    }

    private BigDecimal buscarEnLineas(List<String> lineas, String etiqueta,
                                       String separadorMiles, String separadorDecimales) {
        if (etiqueta == null || etiqueta.isBlank() || lineas == null) return null;
        String etiquetaLower = etiqueta.toLowerCase();
        for (String linea : lineas) {
            if (linea == null) continue;
            int idx = linea.toLowerCase().indexOf(etiquetaLower);
            if (idx < 0) continue;
            String resto = linea.substring(idx + etiqueta.length());
            BigDecimal valor = extraerNumero(resto, separadorMiles, separadorDecimales);
            if (valor != null) return valor;
        }
        return null;
    }

    private BigDecimal buscarEnCeldas(List<List<String>> filas, String etiqueta,
                                       String separadorMiles, String separadorDecimales) {
        if (etiqueta == null || etiqueta.isBlank() || filas == null) return null;
        String etiquetaLower = etiqueta.toLowerCase();

        for (int fila = 0; fila < filas.size(); fila++) {
            List<String> celdas = filas.get(fila);
            if (celdas == null) continue;
            for (int col = 0; col < celdas.size(); col++) {
                String celda = celdas.get(col);
                if (celda == null || !celda.toLowerCase().contains(etiquetaLower)) continue;

                // 1. Misma fila, siguiente columna
                if (col + 1 < celdas.size()) {
                    BigDecimal valor = extraerNumero(celdas.get(col + 1), separadorMiles, separadorDecimales);
                    if (valor != null) return valor;
                }
                // 2. Misma columna, fila siguiente (bloques tipo "encabezado / valores")
                if (fila + 1 < filas.size()) {
                    List<String> siguienteFila = filas.get(fila + 1);
                    if (siguienteFila != null && col < siguienteFila.size()) {
                        BigDecimal valor = extraerNumero(siguienteFila.get(col), separadorMiles, separadorDecimales);
                        if (valor != null) return valor;
                    }
                }
            }
        }
        return null;
    }

    private BigDecimal extraerNumero(String texto, String separadorMiles, String separadorDecimales) {
        if (texto == null) return null;
        Matcher m = NUMERO.matcher(texto);
        if (!m.find()) return null;
        return montoParser.parsear(m.group(), separadorMiles, separadorDecimales, BigDecimal.ONE);
    }

    private ResultadoCuadre construir(BigDecimal saldoAnterior, BigDecimal creditos,
                                       BigDecimal debitos, BigDecimal saldoFinal, BigDecimal tolerancia) {
        List<String> advertencias = new ArrayList<>();
        if (saldoAnterior == null) advertencias.add("No se encontró el saldo anterior en el archivo");
        if (creditos == null) advertencias.add("No se encontró el total de créditos en el archivo");
        if (debitos == null) advertencias.add("No se encontró el total de débitos en el archivo");
        if (saldoFinal == null) advertencias.add("No se encontró el saldo final en el archivo");

        if (!advertencias.isEmpty()) {
            return ResultadoCuadre.builder()
                    .habilitada(true)
                    .saldoAnterior(saldoAnterior).creditos(creditos).debitos(debitos).saldoFinal(saldoFinal)
                    .cuadra(false)
                    .advertencias(advertencias)
                    .build();
        }

        BigDecimal saldoCalculado = saldoAnterior.add(creditos).subtract(debitos);
        BigDecimal diferencia = saldoCalculado.subtract(saldoFinal).abs();
        BigDecimal tol = tolerancia != null ? tolerancia : new BigDecimal("0.01");
        boolean cuadra = diferencia.compareTo(tol) <= 0;

        return ResultadoCuadre.builder()
                .habilitada(true)
                .saldoAnterior(saldoAnterior).creditos(creditos).debitos(debitos).saldoFinal(saldoFinal)
                .saldoCalculado(saldoCalculado).diferencia(diferencia)
                .cuadra(cuadra)
                .advertencias(cuadra ? List.of() : List.of(
                        "El saldo calculado (" + saldoCalculado + ") no coincide con el saldo final del archivo (" + saldoFinal + ")"))
                .build();
    }
}
