package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.model.extractoconfig.TipoOrigenExtracto;
import com.conciliacion.bancaria.domain.service.parser.support.ContinuacionLineaHelper;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.LineasTextoReader;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.domain.service.parser.support.SignoResolver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Usa subclases de las 3 estrategias reales (en vez de mocks) que solo cuentan
 * invocaciones — evita depender de la capacidad de Mockito de subclasificar
 * clases concretas, que en este entorno (JDK 25 + versión de Byte Buddy del
 * proyecto) no funciona de forma confiable, a diferencia de mockear interfaces.
 */
@DisplayName("BankStatementParserDispatcher — despacha por tipoOrigen, no por tipo de archivo ni banco")
class BankStatementParserDispatcherTest {

    private ContadorDelimitado delimitadoParser;
    private ContadorAnchoFijo anchoFijoParser;
    private ContadorExcel excelParser;
    private BankStatementParserDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        delimitadoParser = new ContadorDelimitado();
        anchoFijoParser = new ContadorAnchoFijo();
        excelParser = new ContadorExcel();
        dispatcher = new BankStatementParserDispatcher(delimitadoParser, anchoFijoParser, excelParser);
    }

    private ConfiguracionExtractoDetalle configCon(TipoOrigenExtracto tipo) {
        ConfiguracionExtractoDetalle config = new ConfiguracionExtractoDetalle();
        config.setTipoOrigen(tipo);
        return config;
    }

    @Test
    @DisplayName("tipoOrigen=DELIMITADO despacha solo al parser delimitado")
    void despachaDelimitado() {
        dispatcher.parsear("x".getBytes(), configCon(TipoOrigenExtracto.DELIMITADO), "2026-06");

        assertThat(delimitadoParser.invocaciones).isEqualTo(1);
        assertThat(anchoFijoParser.invocaciones).isZero();
        assertThat(excelParser.invocaciones).isZero();
    }

    @Test
    @DisplayName("tipoOrigen=ANCHO_FIJO despacha solo al parser de ancho fijo")
    void despachaAnchoFijo() {
        dispatcher.parsear("x".getBytes(), configCon(TipoOrigenExtracto.ANCHO_FIJO), "2026-06");

        assertThat(anchoFijoParser.invocaciones).isEqualTo(1);
        assertThat(delimitadoParser.invocaciones).isZero();
        assertThat(excelParser.invocaciones).isZero();
    }

    @Test
    @DisplayName("tipoOrigen=EXCEL despacha solo al parser de excel")
    void despachaExcel() {
        dispatcher.parsear("x".getBytes(), configCon(TipoOrigenExtracto.EXCEL), "2026-06");

        assertThat(excelParser.invocaciones).isEqualTo(1);
        assertThat(delimitadoParser.invocaciones).isZero();
        assertThat(anchoFijoParser.invocaciones).isZero();
    }

    @Test
    @DisplayName("configuracion sin tipoOrigen lanza CsvValidationException, no NullPointerException")
    void sinTipoOrigenLanzaExcepcionClara() {
        ConfiguracionExtractoDetalle config = new ConfiguracionExtractoDetalle();

        assertThatThrownBy(() -> dispatcher.parsear("x".getBytes(), config, "2026-06"))
                .isInstanceOf(CsvValidationException.class);
    }

    // ── Dobles de prueba: cuentan invocaciones sin ejecutar lógica real ─────

    private static class ContadorDelimitado extends DelimitadoBankStatementParser {
        int invocaciones = 0;

        ContadorDelimitado() {
            super(new MontoParser(), new FechaResolver(), new SignoResolver(), new CuadreValidator(new MontoParser()), new LineasTextoReader());
        }

        @Override
        public List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo) {
            invocaciones++;
            return List.of();
        }
    }

    private static class ContadorAnchoFijo extends AnchoFijoBankStatementParser {
        int invocaciones = 0;

        ContadorAnchoFijo() {
            super(new MontoParser(), new FechaResolver(), new SignoResolver(),
                    new ContinuacionLineaHelper(), new CuadreValidator(new MontoParser()), new LineasTextoReader());
        }

        @Override
        public List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo) {
            invocaciones++;
            return List.of();
        }
    }

    private static class ContadorExcel extends ExcelBankStatementParser {
        int invocaciones = 0;

        ContadorExcel() {
            super(new MontoParser(), new FechaResolver(), new ContinuacionLineaHelper(), new CuadreValidator(new MontoParser()));
        }

        @Override
        public List<Movimiento> parsear(byte[] contenido, ConfiguracionExtractoDetalle config, String periodo) {
            invocaciones++;
            return List.of();
        }
    }
}
