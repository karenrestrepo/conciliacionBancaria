package com.conciliacion.bancaria.domain.service.parser.support;

import com.conciliacion.bancaria.domain.model.extractoconfig.ConvencionSigno;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SignoResolver")
class SignoResolverTest {

    private final SignoResolver resolver = new SignoResolver();

    @Test
    @DisplayName("sufijo '+' es credito, '-' es debito")
    void sufijo() {
        assertThat(resolver.esCreditoPorSigno("2,364,110.00+", ConvencionSigno.SUFIJO)).isTrue();
        assertThat(resolver.esCreditoPorSigno("2,364,110.00-", ConvencionSigno.SUFIJO)).isFalse();
    }

    @Test
    @DisplayName("prefijo '+' es credito, '-' es debito")
    void prefijo() {
        assertThat(resolver.esCreditoPorSigno("+2,364,110.00", ConvencionSigno.PREFIJO)).isTrue();
        assertThat(resolver.esCreditoPorSigno("-2,364,110.00", ConvencionSigno.PREFIJO)).isFalse();
    }

    @Test
    @DisplayName("limpia el caracter de signo del texto")
    void limpiaSigno() {
        assertThat(resolver.limpiarSigno("2,364,110.00-", ConvencionSigno.SUFIJO)).isEqualTo("2,364,110.00");
        assertThat(resolver.limpiarSigno("-2,364,110.00", ConvencionSigno.PREFIJO)).isEqualTo("2,364,110.00");
    }

    @Test
    @DisplayName("columna tipo literal CREDITO/DEBITO o C/D")
    void columnaTipoLiteral() {
        assertThat(resolver.esCreditoPorTipoLiteral("CREDITO")).isTrue();
        assertThat(resolver.esCreditoPorTipoLiteral("C")).isTrue();
        assertThat(resolver.esCreditoPorTipoLiteral("DEBITO")).isFalse();
        assertThat(resolver.esCreditoPorTipoLiteral("D")).isFalse();
    }

    @Test
    @DisplayName("tipoDe traduce booleano a literal DEBITO/CREDITO")
    void tipoDe() {
        assertThat(resolver.tipoDe(true)).isEqualTo("CREDITO");
        assertThat(resolver.tipoDe(false)).isEqualTo("DEBITO");
    }
}
