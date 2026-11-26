package com.conciliacion.bancaria.domain.service;

import com.conciliacion.bancaria.domain.model.MapeoAuxiliar;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.RevisionLectura.EstadoCuadre;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import com.conciliacion.bancaria.domain.model.TablaArchivo.Celda;
import com.conciliacion.bancaria.domain.service.InterpreteAuxiliar.Interpretacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterpreteAuxiliar — lee y verifica auxiliares de distintas estructuras (datos ficticios)")
class InterpreteAuxiliarTest {

    private final InterpreteAuxiliar interprete = new InterpreteAuxiliar();

    /** Cada texto es una fila; las celdas se separan con "|". */
    private static TablaArchivo tabla(String... filas) {
        List<List<Celda>> celdas = Arrays.stream(filas)
                .map(f -> Arrays.stream(f.split("\\|", -1)).map(Celda::deTexto).toList())
                .toList();
        return new TablaArchivo(List.of(new TablaArchivo.Hoja("auxiliar", celdas)));
    }

    private static MapeoAuxiliar.MapeoAuxiliarBuilder dosColumnas() {
        return MapeoAuxiliar.builder()
                .hoja(0).primeraFilaDatos(1)
                .columnaFecha(0).columnaComprobante(1).columnasDescripcion(List.of(2))
                .esquemaMonto("DEBITO_CREDITO").columnaDebito(3).columnaCredito(4)
                .separadorDecimal(",").ordenFecha("DMA");
    }

    private static MapeoAuxiliar.Posicion en(int fila, int columna) {
        return new MapeoAuxiliar.Posicion(fila, columna);
    }

    private static final String[] EJEMPLO = {
            "Fecha|Documento|Detalle|Débito|Crédito",
            "||SALDO ANTERIOR||",
            "01/12/2026|RC-1|Abono cliente|1.500,50|0,00",
            "02/12/2026|CE-7|Pago proveedor||300,25",
            "||TOTALES|1.500,50|300,25",
    };

    @Test
    @DisplayName("caso normal: el débito contable entra al banco y cuadra con los totales")
    void casoNormal() {
        Interpretacion r = interprete.interpretar(tabla(EJEMPLO),
                dosColumnas().totalDebitos(en(4, 3)).totalCreditos(en(4, 4)).build());

        assertThat(r.revision().getErrores()).isEmpty();
        assertThat(r.revision().getEstadoCuadre()).isEqualTo(EstadoCuadre.CUADRA);
        assertThat(r.movimientos()).extracting(Movimiento::getTipo).containsExactly("CREDITO", "DEBITO");
        Movimiento primero = r.movimientos().get(0);
        assertThat(primero.getMonto()).isEqualByComparingTo("1500.50");
        assertThat(primero.getFecha()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(primero.getNumeroComprobante()).isEqualTo("RC-1");
        assertThat(primero.getDescripcion()).isEqualTo("RC-1 — Abono cliente");
    }

    @Test
    @DisplayName("si falta un movimiento, no cuadra")
    void noCuadra() {
        // La IA indicó mal la primera fila y se salta un movimiento.
        Interpretacion r = interprete.interpretar(tabla(EJEMPLO),
                dosColumnas().primeraFilaDatos(3).totalDebitos(en(4, 3)).totalCreditos(en(4, 4)).build());

        assertThat(r.revision().getEstadoCuadre()).isEqualTo(EstadoCuadre.NO_CUADRA);
    }

    @Test
    @DisplayName("datos faltantes: sin saldos ni totales no se puede verificar")
    void sinControles() {
        Interpretacion r = interprete.interpretar(tabla(EJEMPLO), dosColumnas().build());

        assertThat(r.movimientos()).hasSize(2);
        assertThat(r.revision().getEstadoCuadre()).isEqualTo(EstadoCuadre.NO_VERIFICABLE);
        assertThat(r.revision().getAdvertencias()).anyMatch(a -> a.contains("sin fecha"));
    }

    @Test
    @DisplayName("un solo total no basta para dar por verificado el archivo")
    void unSoloTotal() {
        Interpretacion r = interprete.interpretar(tabla(EJEMPLO), dosColumnas().totalDebitos(en(4, 3)).build());

        assertThat(r.revision().getEstadoCuadre()).isEqualTo(EstadoCuadre.NO_VERIFICABLE);
    }

    @Test
    @DisplayName("otra estructura: un valor con signo al final, punto decimal y fecha con mes en letras")
    void valorConSigno() {
        TablaArchivo t = tabla(
                "Saldo inicial|1,000.00",
                "Concepto|Valor|Fecha",
                "Consignación|2,000.00+|1-dic-2026",
                "Cheque|500.00-|Dec 3, 2026",
                "Saldo final|2,500.00");
        MapeoAuxiliar mapeo = MapeoAuxiliar.builder()
                .hoja(0).primeraFilaDatos(2)
                .columnaFecha(2).columnaComprobante(-1).columnasDescripcion(List.of(0))
                .esquemaMonto("MONTO_CON_SIGNO").columnaMonto(1).positivoEsDebito(true)
                .separadorDecimal(".").ordenFecha("DMA")
                .saldoInicial(en(0, 1)).saldoFinal(en(4, 1))
                .build();

        Interpretacion r = interprete.interpretar(t, mapeo);

        // "Dec 3, 2026" lleva coma: se normaliza igual que los demás formatos.
        assertThat(r.movimientos()).extracting(Movimiento::getFecha)
                .containsExactly(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3));
        assertThat(r.movimientos()).extracting(Movimiento::getTipo).containsExactly("CREDITO", "DEBITO");
        assertThat(r.movimientos().get(0).getNumeroComprobante()).isNull();
        assertThat(r.revision().getEstadoCuadre()).isEqualTo(EstadoCuadre.CUADRA);
    }

    @Test
    @DisplayName("otra estructura: valor más columna de naturaleza D/C")
    void valorYNaturaleza() {
        TablaArchivo t = tabla(
                "Fecha|Nat|Valor|Glosa",
                "2026/12/05|D|100|Ingreso",
                "20261206|C|40|Egreso");
        MapeoAuxiliar mapeo = MapeoAuxiliar.builder()
                .hoja(0).primeraFilaDatos(1)
                .columnaFecha(0).columnasDescripcion(List.of(3))
                .esquemaMonto("MONTO_Y_NATURALEZA").columnaMonto(2).columnaNaturaleza(1)
                .valoresDebito(List.of("D")).separadorDecimal(".").ordenFecha("AMD")
                .build();

        Interpretacion r = interprete.interpretar(t, mapeo);

        assertThat(r.movimientos()).extracting(Movimiento::getTipo).containsExactly("CREDITO", "DEBITO");
    }

    @Test
    @DisplayName("saldos que solo coinciden con el sentido invertido piden confirmación")
    void sentidoDudoso() {
        TablaArchivo t = tabla(
                "100,00|0,00",
                "01/12/2026|RC-1|Abono|50,00|0,00",
                "60,00|50,00");
        Interpretacion r = interprete.interpretar(t,
                dosColumnas().saldoInicial(en(0, 0)).saldoFinal(en(2, 1)).build());

        assertThat(r.revision().getEstadoCuadre()).isEqualTo(EstadoCuadre.NO_VERIFICABLE);
        assertThat(r.revision().getAdvertencias()).anyMatch(a -> a.contains("sentido"));
    }

    @Test
    @DisplayName("un valor ilegible en una fila con fecha impide cargar")
    void valorIlegible() {
        TablaArchivo t = tabla("encabezado", "01/12/2026|RC-1|Abono|1O0,00|");

        Interpretacion r = interprete.interpretar(t, dosColumnas().build());

        assertThat(r.revision().getErrores()).anyMatch(e -> e.startsWith("Fila 2"));
    }

    @Test
    @DisplayName("un mapeo con columnas que faltan se reporta como error")
    void mapeoIncompleto() {
        Interpretacion r = interprete.interpretar(tabla(EJEMPLO), dosColumnas().columnaCredito(-1).build());

        assertThat(r.revision().getErrores()).containsExactly("No se identificaron las columnas de valores");
        assertThat(r.movimientos()).isEmpty();
    }

    @Test
    @DisplayName("números: paréntesis, moneda y miles con punto")
    void numeros() {
        assertThat(interprete.leerNumero(Celda.deTexto("($ 1.234.567,8)"), ",")).isEqualByComparingTo("-1234567.80");
        assertThat(interprete.leerNumero(Celda.deTexto("-2,500.10"), ".")).isEqualByComparingTo("-2500.10");
    }
}
