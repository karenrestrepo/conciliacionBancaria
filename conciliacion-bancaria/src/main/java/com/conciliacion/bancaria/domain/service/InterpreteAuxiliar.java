package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.AuxiliarLeido;
import com.conciliacion.bancaria.domain.model.LecturaExtracto.MovimientoLeido;
import com.conciliacion.bancaria.domain.model.MapeoColumnas;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.RevisionAuxiliar;
import com.conciliacion.bancaria.domain.model.RevisionLectura.EstadoCuadre;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import com.conciliacion.bancaria.domain.model.TablaArchivo.Celda;
import com.conciliacion.bancaria.shared.EstadoMovimiento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee todas las filas de un auxiliar siguiendo el {@link MapeoColumnas} y verifica el
 * resultado contra los saldos y totales del propio archivo. No usa IA.
 */
public class InterpreteAuxiliar {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");
    private static final int MAX_ERRORES = 5;
    private static final Pattern FECHA = Pattern.compile(
            "^(\\d{1,4}|\\p{L}{3,10})[/\\-. ]+(?:de )?(\\d{1,2}|\\p{L}{3,10})\\.?[/\\-., ]+(?:de )?(\\d{1,4})(?:[ T].*)?$");
    private static final Pattern FECHA_SIN_ANIO = Pattern.compile(
            "^(\\d{1,2}|\\p{L}{3,10})[/\\-. ]+(?:de )?(\\d{1,2}|\\p{L}{3,10})\\.?$");
    private static final Pattern FECHA_COMPACTA = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
    private static final List<String> MESES =
            List.of("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic");
    private static final List<String> MONTHS =
            List.of("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec");

    public record Interpretacion(RevisionAuxiliar revision, List<Movimiento> movimientos) {}

    /** Débito y crédito contables de una fila, con el signo que traen en el archivo. */
    private record Valores(BigDecimal debito, BigDecimal credito) {
        boolean enCero() {
            return debito.signum() == 0 && credito.signum() == 0;
        }
    }

    public Interpretacion interpretar(TablaArchivo tabla, MapeoColumnas mapeo) {
        return interpretar(tabla, mapeo, null);
    }

    /** @param periodo permite completar el año en archivos cuyas fechas solo traen día y mes */
    public Interpretacion interpretar(TablaArchivo tabla, MapeoColumnas mapeo, YearMonth periodo) {
        List<String> errores = validar(tabla, mapeo);
        if (!errores.isEmpty()) {
            return new Interpretacion(RevisionAuxiliar.builder()
                    .mapeo(mapeo).movimientos(List.of())
                    .sumaDebitos(BigDecimal.ZERO).sumaCreditos(BigDecimal.ZERO)
                    .estadoCuadre(EstadoCuadre.NO_VERIFICABLE)
                    .errores(errores).advertencias(List.of())
                    .build(), List.of());
        }

        TablaArchivo.Hoja hoja = tabla.hojas().get(mapeo.getHoja());
        Set<Integer> filasDeControl = filasDeControl(mapeo);
        List<Movimiento> movimientos = new ArrayList<>();
        BigDecimal sumaDebitos = BigDecimal.ZERO;
        BigDecimal sumaCreditos = BigDecimal.ZERO;
        int sinFecha = 0;
        int ilegibles = 0;
        int rotulos = 0;

        for (int f = Math.max(0, mapeo.getPrimeraFilaDatos()); f < hoja.filas().size(); f++) {
            if (filasDeControl.contains(f)) continue;
            Celda celdaFecha = hoja.celda(f, mapeo.getColumnaFecha());
            LocalDate fecha = leerFecha(celdaFecha, mapeo.getOrdenFecha());
            if (fecha == null && periodo != null) {
                fecha = fechaSinAnio(celdaFecha.texto(), mapeo.getOrdenFecha(), periodo);
            }
            Valores valores;
            try {
                valores = leerValores(hoja, f, mapeo);
            } catch (NumberFormatException e) {
                // Sin fecha, o con solo texto donde iría el valor, suele ser un encabezado repetido
                // en cada página; con fecha y cifras es un movimiento que no se pudo leer.
                if (fecha != null && !tieneCifras(hoja, f, mapeo)) {
                    rotulos++;
                } else if (fecha != null && ++ilegibles <= MAX_ERRORES) {
                    errores.add("Fila " + (f + 1) + ": " + e.getMessage());
                }
                continue;
            }
            if (valores.enCero()) continue;
            if (fecha == null) {
                sinFecha++;
                continue;
            }
            sumaDebitos = sumaDebitos.add(valores.debito());
            sumaCreditos = sumaCreditos.add(valores.credito());

            String comprobante = existe(mapeo.getColumnaComprobante())
                    ? recortar(hoja.celda(f, mapeo.getColumnaComprobante()).texto(), 50) : "";
            String descripcion = descripcion(hoja, f, mapeo, comprobante);
            // Un débito en la cuenta del banco es dinero que entra: CREDITO en el extracto.
            agregar(movimientos, fecha, comprobante, descripcion, valores.debito(), "CREDITO", "DEBITO");
            agregar(movimientos, fecha, comprobante, descripcion, valores.credito(), "DEBITO", "CREDITO");
        }
        if (ilegibles > MAX_ERRORES) {
            errores.add("Hay " + (ilegibles - MAX_ERRORES) + " filas más con valores ilegibles");
        }
        if (movimientos.isEmpty()) {
            errores.add("No se encontraron movimientos en el archivo");
        }

        List<String> advertencias = new ArrayList<>();
        BigDecimal saldoInicial = leerControl(hoja, mapeo.getSaldoInicial(), mapeo);
        BigDecimal saldoFinal = leerControl(hoja, mapeo.getSaldoFinal(), mapeo);
        BigDecimal totalDebitos = leerControl(hoja, mapeo.getTotalDebitos(), mapeo);
        BigDecimal totalCreditos = leerControl(hoja, mapeo.getTotalCreditos(), mapeo);
        EstadoCuadre estado = verificar(sumaDebitos, sumaCreditos, totalDebitos, totalCreditos,
                saldoInicial, saldoFinal, advertencias);
        if (sinFecha > 0 && estado != EstadoCuadre.CUADRA) {
            advertencias.add(sinFecha + " filas con valores pero sin fecha no se incluyeron (suelen ser totales)");
        }

        if (rotulos > 0 && estado != EstadoCuadre.CUADRA) {
            advertencias.add(rotulos + " filas con texto en lugar de un valor no se incluyeron "
                    + "(suelen ser encabezados repetidos)");
        }

        RevisionAuxiliar revision = RevisionAuxiliar.builder()
                .mapeo(mapeo)
                .movimientos(movimientos.stream()
                        .map(m -> MovimientoLeido.builder()
                                .fecha(m.getFecha().toString()).descripcion(m.getDescripcion())
                                .monto(m.getMonto()).tipo(m.getTipo()).build())
                        .toList())
                .sumaDebitos(sumaDebitos).sumaCreditos(sumaCreditos)
                .totalDebitosArchivo(totalDebitos).totalCreditosArchivo(totalCreditos)
                .saldoInicial(saldoInicial).saldoFinal(saldoFinal)
                .estadoCuadre(estado)
                .errores(errores).advertencias(advertencias)
                .build();
        return new Interpretacion(revision, movimientos);
    }

    /** Verifica una transcripción de la IA con las mismas reglas que un archivo tabular. */
    public Interpretacion interpretar(AuxiliarLeido leido) {
        List<AuxiliarLeido.Fila> filas = leido == null || leido.getMovimientos() == null
                ? List.of() : leido.getMovimientos();
        List<String> errores = new ArrayList<>();
        List<List<Celda>> celdas = new ArrayList<>();
        // La fila 0 guarda los saldos y totales del documento.
        celdas.add(List.of(numero(leido == null ? null : leido.getSaldoInicial()),
                numero(leido == null ? null : leido.getSaldoFinal()),
                numero(leido == null ? null : leido.getTotalDebitos()),
                numero(leido == null ? null : leido.getTotalCreditos())));
        for (int i = 0; i < filas.size(); i++) {
            AuxiliarLeido.Fila fila = filas.get(i);
            Celda fecha = Celda.deTexto(fila.getFecha());
            if (leerFecha(fecha, "AMD") == null && errores.size() < MAX_ERRORES) {
                errores.add("Movimiento " + (i + 1) + ": no se pudo leer la fecha");
            }
            celdas.add(List.of(fecha, Celda.deTexto(fila.getComprobante()), Celda.deTexto(fila.getDescripcion()),
                    numero(fila.getDebito()), numero(fila.getCredito())));
        }

        MapeoColumnas mapeo = MapeoColumnas.builder()
                .hoja(0).primeraFilaDatos(1)
                .columnaFecha(0).columnaComprobante(1).columnasDescripcion(List.of(2))
                .esquemaMonto("DEBITO_CREDITO").columnaDebito(3).columnaCredito(4)
                .separadorDecimal(".").ordenFecha("AMD")
                .saldoInicial(new MapeoColumnas.Posicion(0, 0)).saldoFinal(new MapeoColumnas.Posicion(0, 1))
                .totalDebitos(new MapeoColumnas.Posicion(0, 2)).totalCreditos(new MapeoColumnas.Posicion(0, 3))
                .build();
        Interpretacion base = interpretar(
                new TablaArchivo(List.of(new TablaArchivo.Hoja("documento", celdas))), mapeo);
        errores.addAll(base.revision().getErrores());
        // El mapeo es interno: no tiene sentido devolverlo para confirmar.
        return new Interpretacion(base.revision().toBuilder().mapeo(null).errores(errores).build(),
                base.movimientos());
    }

    private Celda numero(BigDecimal valor) {
        return valor == null ? Celda.VACIA : new Celda(valor.toPlainString(), valor, null);
    }

    private List<String> validar(TablaArchivo tabla, MapeoColumnas m) {
        List<String> errores = new ArrayList<>();
        if (m == null) {
            errores.add("No se pudo identificar la estructura del archivo");
            return errores;
        }
        if (m.getHoja() < 0 || m.getHoja() >= tabla.hojas().size()) {
            errores.add("La hoja indicada no existe");
        }
        if (!existe(m.getColumnaFecha())) {
            errores.add("No se identificó la columna de fecha");
        }
        boolean columnas = switch (String.valueOf(m.getEsquemaMonto())) {
            case "DEBITO_CREDITO" -> existe(m.getColumnaDebito()) && existe(m.getColumnaCredito())
                    && !m.getColumnaDebito().equals(m.getColumnaCredito());
            case "MONTO_CON_SIGNO" -> existe(m.getColumnaMonto());
            case "MONTO_Y_NATURALEZA" -> existe(m.getColumnaMonto()) && existe(m.getColumnaNaturaleza())
                    && m.getValoresDebito() != null && !m.getValoresDebito().isEmpty();
            default -> false;
        };
        if (!columnas) {
            errores.add("No se identificaron las columnas de valores");
        }
        if (!".".equals(m.getSeparadorDecimal()) && !",".equals(m.getSeparadorDecimal())) {
            errores.add("No se identificó el separador decimal");
        }
        return errores;
    }

    private boolean existe(Integer columna) {
        return columna != null && columna >= 0;
    }

    private Set<Integer> filasDeControl(MapeoColumnas m) {
        Set<Integer> filas = new HashSet<>();
        for (MapeoColumnas.Posicion p : new MapeoColumnas.Posicion[]{
                m.getSaldoInicial(), m.getSaldoFinal(), m.getTotalDebitos(), m.getTotalCreditos()}) {
            if (p != null) filas.add(p.getFila());
        }
        return filas;
    }

    private Valores leerValores(TablaArchivo.Hoja hoja, int f, MapeoColumnas m) {
        String separador = m.getSeparadorDecimal();
        BigDecimal cero = BigDecimal.ZERO;
        if ("DEBITO_CREDITO".equals(m.getEsquemaMonto())) {
            return new Valores(leerNumero(hoja.celda(f, m.getColumnaDebito()), separador),
                    leerNumero(hoja.celda(f, m.getColumnaCredito()), separador));
        }
        BigDecimal monto = leerNumero(hoja.celda(f, m.getColumnaMonto()), separador);
        if (monto.signum() == 0) return new Valores(cero, cero);
        if ("MONTO_CON_SIGNO".equals(m.getEsquemaMonto())) {
            boolean esDebito = (monto.signum() > 0) == m.isPositivoEsDebito();
            return esDebito ? new Valores(monto.abs(), cero) : new Valores(cero, monto.abs());
        }
        String naturaleza = hoja.celda(f, m.getColumnaNaturaleza()).texto().trim();
        if (naturaleza.isEmpty()) {
            throw new NumberFormatException("no indica si el valor es débito o crédito");
        }
        boolean esDebito = m.getValoresDebito().stream().anyMatch(v -> v.trim().equalsIgnoreCase(naturaleza));
        return esDebito ? new Valores(monto, cero) : new Valores(cero, monto);
    }

    private boolean tieneCifras(TablaArchivo.Hoja hoja, int f, MapeoColumnas m) {
        List<Integer> columnas = "DEBITO_CREDITO".equals(m.getEsquemaMonto())
                ? List.of(m.getColumnaDebito(), m.getColumnaCredito())
                : List.of(m.getColumnaMonto());
        return columnas.stream()
                .map(c -> hoja.celda(f, c))
                .anyMatch(c -> c.numero() != null || c.texto().chars().anyMatch(Character::isDigit));
    }

    /** Acepta signo antes o después, paréntesis, símbolo de moneda y separadores de miles. */
    BigDecimal leerNumero(Celda celda, String separadorDecimal) {
        if (celda.numero() != null) return celda.numero().setScale(2, RoundingMode.HALF_UP);
        String texto = celda.texto().replaceAll("(?i)COP|USD|\\$|\\s|\\u00a0", "");
        if (texto.isEmpty() || texto.equals("-")) return BigDecimal.ZERO;

        boolean negativo = false;
        if (texto.startsWith("(") && texto.endsWith(")")) {
            negativo = true;
            texto = texto.substring(1, texto.length() - 1);
        }
        if (texto.startsWith("-") || texto.startsWith("+")) {
            negativo ^= texto.charAt(0) == '-';
            texto = texto.substring(1);
        } else if (texto.endsWith("-") || texto.endsWith("+")) {
            negativo ^= texto.endsWith("-");
            texto = texto.substring(0, texto.length() - 1);
        }
        String miles = ",".equals(separadorDecimal) ? "." : ",";
        texto = texto.replace(miles, "").replace("'", "").replace(separadorDecimal, ".");
        if (!texto.matches("\\d+(\\.\\d+)?|\\.\\d+")) {
            throw new NumberFormatException("el valor '" + celda.texto() + "' no es un número");
        }
        BigDecimal valor = new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
        return negativo ? valor.negate() : valor;
    }

    /** Null si la celda no contiene una fecha (encabezados, totales, filas vacías). */
    LocalDate leerFecha(Celda celda, String orden) {
        if (celda.fecha() != null) return celda.fecha();
        String texto = celda.texto().trim();
        if (celda.numero() != null) {
            long n = celda.numero().longValue();
            // Número de serie de Excel (fecha sin formato de fecha).
            if (n > 20_000 && n < 80_000) return LocalDate.of(1899, 12, 30).plusDays(n);
        }
        Matcher compacta = FECHA_COMPACTA.matcher(texto);
        if (compacta.matches()) {
            return fecha(compacta.group(1), compacta.group(2), compacta.group(3));
        }
        Matcher m = FECHA.matcher(texto);
        if (!m.matches()) return null;
        String a = m.group(1), b = m.group(2), c = m.group(3);
        if (a.length() == 4) return fecha(a, b, c);
        boolean mesPrimero = "MDA".equals(orden) || mes(a) > 0 && !a.matches("\\d+");
        return mesPrimero ? fecha(c, a, b) : fecha(c, b, a);
    }

    /** "05/12" o "5 dic": toma el año del período, o el vecino si el movimiento cruza de año. */
    private LocalDate fechaSinAnio(String texto, String orden, YearMonth periodo) {
        Matcher m = FECHA_SIN_ANIO.matcher(texto.trim());
        if (!m.matches()) return null;
        String a = m.group(1), b = m.group(2);
        boolean mesPrimero = !a.matches("\\d+") || !"DMA".equals(orden) && b.matches("\\d+");
        String mes = mesPrimero ? a : b;
        String dia = mesPrimero ? b : a;
        int distancia = mes(mes) - periodo.getMonthValue();
        int anio = periodo.getYear() + (distancia > 6 ? -1 : distancia < -6 ? 1 : 0);
        return fecha(String.valueOf(anio), mes, dia);
    }

    private LocalDate fecha(String anio, String mes, String dia) {
        if (!anio.matches("\\d{2}|\\d{4}") || !dia.matches("\\d{1,2}")) return null;
        int y = Integer.parseInt(anio);
        if (anio.length() == 2) y += 2000;
        try {
            return LocalDate.of(y, mes(mes), Integer.parseInt(dia));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Número de mes a partir de "03", "mar", "Marzo" o "March"; 0 si no lo es. */
    private int mes(String texto) {
        if (texto.matches("\\d{1,2}")) return Integer.parseInt(texto);
        if (texto.length() < 3) return 0;
        String tres = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .substring(0, 3).toLowerCase();
        int es = MESES.indexOf(tres);
        return (es >= 0 ? es : MONTHS.indexOf(tres)) + 1;
    }

    private String descripcion(TablaArchivo.Hoja hoja, int f, MapeoColumnas m, String comprobante) {
        List<String> partes = new ArrayList<>();
        if (!comprobante.isEmpty()) partes.add(comprobante);
        if (m.getColumnasDescripcion() != null) {
            for (Integer columna : m.getColumnasDescripcion()) {
                if (!existe(columna) || columna.equals(m.getColumnaComprobante())) continue;
                String texto = hoja.celda(f, columna).texto();
                if (!texto.isEmpty()) partes.add(texto);
            }
        }
        return partes.isEmpty() ? "(sin descripción)" : recortar(String.join(" — ", partes), 255);
    }

    private void agregar(List<Movimiento> movimientos, LocalDate fecha, String comprobante, String descripcion,
                         BigDecimal valor, String tipoSiPositivo, String tipoSiNegativo) {
        if (valor.signum() == 0) return;
        movimientos.add(Movimiento.builder()
                .fecha(fecha)
                .descripcion(descripcion)
                .monto(valor.abs())
                .tipo(valor.signum() > 0 ? tipoSiPositivo : tipoSiNegativo)
                .estado(EstadoMovimiento.PENDIENTE)
                .numeroComprobante(comprobante.isEmpty() ? null : comprobante)
                .build());
    }

    private BigDecimal leerControl(TablaArchivo.Hoja hoja, MapeoColumnas.Posicion p, MapeoColumnas m) {
        if (p == null) return null;
        Celda celda = hoja.celda(p.getFila(), p.getColumna());
        if (celda.vacia()) return null;
        try {
            return leerNumero(celda, m.getSeparadorDecimal());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * CUADRA exige los dos totales o los dos saldos. Cualquier comprobación que falle es NO_CUADRA.
     */
    private EstadoCuadre verificar(BigDecimal sumaDebitos, BigDecimal sumaCreditos,
                                   BigDecimal totalDebitos, BigDecimal totalCreditos,
                                   BigDecimal saldoInicial, BigDecimal saldoFinal,
                                   List<String> advertencias) {
        boolean fallo = false;
        if (totalDebitos != null && !iguales(sumaDebitos, totalDebitos)) {
            advertencias.add("Los débitos leídos no coinciden con el total del archivo");
            fallo = true;
        }
        if (totalCreditos != null && !iguales(sumaCreditos, totalCreditos)) {
            advertencias.add("Los créditos leídos no coinciden con el total del archivo");
            fallo = true;
        }
        boolean porTotales = totalDebitos != null && totalCreditos != null;
        boolean porSaldos = false;
        if (saldoInicial != null && saldoFinal != null) {
            BigDecimal neto = sumaDebitos.subtract(sumaCreditos);
            if (iguales(saldoInicial.add(neto), saldoFinal)) {
                porSaldos = true;
            } else if (iguales(saldoInicial.subtract(neto), saldoFinal)) {
                // Propio de cuentas de naturaleza crédito, pero también de columnas invertidas.
                advertencias.add("Los saldos solo coinciden si los débitos restan. Revise el sentido de las columnas");
                return fallo ? EstadoCuadre.NO_CUADRA : EstadoCuadre.NO_VERIFICABLE;
            } else {
                advertencias.add("El saldo inicial más los movimientos no da el saldo final del archivo");
                fallo = true;
            }
        }
        if (fallo) return EstadoCuadre.NO_CUADRA;
        return porTotales || porSaldos ? EstadoCuadre.CUADRA : EstadoCuadre.NO_VERIFICABLE;
    }

    private boolean iguales(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCIA) <= 0;
    }

    private String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
