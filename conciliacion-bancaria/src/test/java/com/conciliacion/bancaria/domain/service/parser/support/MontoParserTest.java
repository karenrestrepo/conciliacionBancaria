package com.conciliacion.bancaria.domain.service.parser.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MontoParser")
class MontoParserTest {

    private final MontoParser parser = new MontoParser();

    @Test
    @DisplayName("formato colombiano: punto miles, coma decimal")
    void formatoColombiano() {
        BigDecimal resultado = parser.parsear("1.234.567,89", ".", ",", BigDecimal.ONE);
        assertThat(resultado).isEqualByComparingTo("1234567.89");
    }

    @Test
    @DisplayName("formato Davivienda: coma miles, punto decimal")
    void formatoDavivienda() {
        BigDecimal resultado = parser.parsear("99,808,949.23", ",", ".", BigDecimal.ONE);
        assertThat(resultado).isEqualByComparingTo("99808949.23");
    }

    @Test
    @DisplayName("aplica factor de escala")
    void aplicaFactor() {
        BigDecimal resultado = parser.parsear("1.000", ".", ",", new BigDecimal("1000"));
        assertThat(resultado).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("ignora simbolo de moneda y espacios")
    void ignoraSimboloMoneda() {
        BigDecimal resultado = parser.parsear(" $ 2,364,110.00 ", ",", ".", BigDecimal.ONE);
        assertThat(resultado).isEqualByComparingTo("2364110.00");
    }

    @Test
    @DisplayName("texto vacio o invalido retorna cero")
    void textoInvalidoRetornaCero() {
        assertThat(parser.parsear("", ",", ".", BigDecimal.ONE)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(parser.parsear(null, ",", ".", BigDecimal.ONE)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(parser.parsear("abc", ",", ".", BigDecimal.ONE)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
