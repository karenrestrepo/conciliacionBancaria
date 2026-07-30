package com.conciliacion.bancaria.domain.service.parser.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FechaResolver")
class FechaResolverTest {

    private final FechaResolver resolver = new FechaResolver();

    @Test
    @DisplayName("extrae anio de un periodo yyyy-MM")
    void extraeAnioDePeriodo() {
        assertThat(resolver.extraerAnio("2026-06")).isEqualTo(2026);
    }

    @Test
    @DisplayName("periodo invalido retorna anio actual")
    void periodoInvalidoRetornaAnioActual() {
        assertThat(resolver.extraerAnio(null)).isEqualTo(LocalDate.now().getYear());
        assertThat(resolver.extraerAnio("ab")).isEqualTo(LocalDate.now().getYear());
    }

    @Test
    @DisplayName("resuelve dia y mes con anio del periodo")
    void resuelveDiaMes() {
        LocalDate fecha = resolver.resolverDiaMes("01", "06", 2026);
        assertThat(fecha).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("dia/mes invalido retorna null en vez de lanzar excepcion")
    void diaMesInvalidoRetornaNull() {
        assertThat(resolver.resolverDiaMes("32", "13", 2026)).isNull();
        assertThat(resolver.resolverDiaMes(null, "06", 2026)).isNull();
    }

    @Test
    @DisplayName("resuelve fecha completa con formato dd/MM/yyyy")
    void resuelveFechaCompleta() {
        LocalDate fecha = resolver.resolverConFormato("15/01/2025", "dd/MM/yyyy", 2025);
        assertThat(fecha).isEqualTo(LocalDate.of(2025, 1, 15));
    }

    @Test
    @DisplayName("infiere anio cuando el formato no lo incluye (fecha tipo d/mm)")
    void infiereAnioSinFormato() {
        LocalDate fecha = resolver.resolverConFormato("1/06", "d/MM", 2026);
        assertThat(fecha).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("hace padding de dia de un solo digito")
    void hacePaddingDeDia() {
        LocalDate fecha = resolver.resolverConFormato("1/6/2026", "dd/MM/yyyy", 2026);
        assertThat(fecha).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("texto de fecha invalido retorna null")
    void textoInvalidoRetornaNull() {
        assertThat(resolver.resolverConFormato("no-es-fecha", "dd/MM/yyyy", 2026)).isNull();
        assertThat(resolver.resolverConFormato(null, "dd/MM/yyyy", 2026)).isNull();
    }
}
