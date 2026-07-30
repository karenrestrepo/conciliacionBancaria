package com.conciliacion.bancaria.domain.service.parser.support;

import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaCuadre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CuadreValidator")
class CuadreValidatorTest {

    private final CuadreValidator validator = new CuadreValidator(new MontoParser());

    private ReglaCuadre reglaDavivienda() {
        ReglaCuadre r = new ReglaCuadre();
        r.setHabilitada(true);
        r.setEtiquetaSaldoAnterior("Saldo Anterior");
        r.setEtiquetaCreditos("Más:Créditos");
        r.setEtiquetaDebitos("Menos: Débitos");
        r.setEtiquetaSaldoFinal("Nuevo Saldo");
        return r;
    }

    @Test
    @DisplayName("regla deshabilitada retorna resultado deshabilitado sin buscar nada")
    void reglaDeshabilitada() {
        ReglaCuadre r = new ReglaCuadre();
        r.setHabilitada(false);
        ResultadoCuadre resultado = validator.validarEnTexto(List.of("cualquier texto"), r, ",", ".");
        assertThat(resultado.isHabilitada()).isFalse();
        assertThat(resultado.isCuadra()).isTrue();
    }

    @Test
    @DisplayName("cuadre correcto en texto tipo Davivienda cuenta corriente (coma miles, punto decimal)")
    void cuadreCorrectoEnTexto() {
        List<String> lineas = List.of(
                "Saldo Anterior                        $80,568,399.90                                                 Días Sobregiro                                 0",
                "Más:Créditos                        $166,072,718.00                                                  Interés de Sobregiro                       $0.00",
                "Menos: Débitos                      $146,832,168.67                                                  Tasa Sobregiro                       28.78% E .A",
                "Nuevo Saldo                           $99,808,949.23                                                 Tasa Mora                            28.78% E .A"
        );

        ResultadoCuadre resultado = validator.validarEnTexto(lineas, reglaDavivienda(), ",", ".");

        assertThat(resultado.isHabilitada()).isTrue();
        assertThat(resultado.getSaldoAnterior()).isEqualByComparingTo("80568399.90");
        assertThat(resultado.getCreditos()).isEqualByComparingTo("166072718.00");
        assertThat(resultado.getDebitos()).isEqualByComparingTo("146832168.67");
        assertThat(resultado.getSaldoFinal()).isEqualByComparingTo("99808949.23");
        assertThat(resultado.isCuadra()).isTrue();
        assertThat(resultado.getAdvertencias()).isEmpty();
    }

    @Test
    @DisplayName("cuadre incorrecto reporta diferencia y advertencia")
    void cuadreIncorrecto() {
        List<String> lineas = List.of(
                "Saldo Anterior $100.00",
                "Más:Créditos $50.00",
                "Menos: Débitos $10.00",
                "Nuevo Saldo $999.00"
        );

        ResultadoCuadre resultado = validator.validarEnTexto(lineas, reglaDavivienda(), ",", ".");

        assertThat(resultado.isCuadra()).isFalse();
        assertThat(resultado.getAdvertencias()).isNotEmpty();
    }

    @Test
    @DisplayName("etiqueta no encontrada produce advertencia y cuadra=false, sin lanzar excepcion")
    void etiquetaNoEncontrada() {
        List<String> lineas = List.of("Este archivo no trae seccion de resumen");

        ResultadoCuadre resultado = validator.validarEnTexto(lineas, reglaDavivienda(), ",", ".");

        assertThat(resultado.isCuadra()).isFalse();
        assertThat(resultado.getAdvertencias()).isNotEmpty();
    }

    @Test
    @DisplayName("modo celdas: etiqueta en fila de encabezado y valor en la misma columna, fila siguiente")
    void cuadreEnCeldasEncabezadoYValorFilaSiguiente() {
        List<List<String>> filas = List.of(
                List.of("SALDO ANTERIOR", "TOTAL ABONOS", "TOTAL CARGOS", "SALDO ACTUAL"),
                List.of("1,049,565,446.68", "532,350,631.00", "1,099,262,464.49", "482,653,613.19")
        );

        ReglaCuadre regla = new ReglaCuadre();
        regla.setHabilitada(true);
        regla.setEtiquetaSaldoAnterior("SALDO ANTERIOR");
        regla.setEtiquetaCreditos("TOTAL ABONOS");
        regla.setEtiquetaDebitos("TOTAL CARGOS");
        regla.setEtiquetaSaldoFinal("SALDO ACTUAL");

        ResultadoCuadre resultado = validator.validarEnCeldas(filas, regla, ",", ".");

        assertThat(resultado.getSaldoAnterior()).isEqualByComparingTo("1049565446.68");
        assertThat(resultado.getSaldoFinal()).isEqualByComparingTo("482653613.19");
        assertThat(resultado.isCuadra()).isTrue();
    }

    @Test
    @DisplayName("modo celdas: etiqueta y valor en la misma fila, columna adyacente")
    void cuadreEnCeldasMismaFila() {
        List<List<String>> filas = List.of(
                List.of("Saldo Anterior", "100.00"),
                List.of("Creditos", "50.00"),
                List.of("Debitos", "10.00"),
                List.of("Saldo Final", "140.00")
        );

        ReglaCuadre regla = new ReglaCuadre();
        regla.setHabilitada(true);
        regla.setEtiquetaSaldoAnterior("Saldo Anterior");
        regla.setEtiquetaCreditos("Creditos");
        regla.setEtiquetaDebitos("Debitos");
        regla.setEtiquetaSaldoFinal("Saldo Final");

        ResultadoCuadre resultado = validator.validarEnCeldas(filas, regla, ",", ".");

        assertThat(resultado.isCuadra()).isTrue();
    }
}
