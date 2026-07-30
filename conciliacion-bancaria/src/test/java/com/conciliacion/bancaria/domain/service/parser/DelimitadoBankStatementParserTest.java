package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfigDelimitado;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.TipoOrigenExtracto;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.LineasTextoReader;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.domain.service.parser.support.SignoResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DelimitadoBankStatementParser")
class DelimitadoBankStatementParserTest {

    private DelimitadoBankStatementParser parser;

    @BeforeEach
    void setUp() {
        parser = new DelimitadoBankStatementParser(
                new MontoParser(), new FechaResolver(), new SignoResolver(),
                new CuadreValidator(new MontoParser()), new LineasTextoReader());
    }

    private ConfiguracionExtractoDetalle configBasica() {
        ConfigDelimitado d = new ConfigDelimitado();
        d.setSeparador(",");
        d.setFilasASaltar(1);
        d.setColumnaFecha(0);
        d.setFormatoFecha("yyyy-MM-dd");
        d.setColumnaDescripcion(1);
        d.setColumnaMonto(2);

        ConfiguracionExtractoDetalle config = new ConfiguracionExtractoDetalle();
        config.setTipoOrigen(TipoOrigenExtracto.DELIMITADO);
        config.setEncoding("UTF-8");
        config.setSeparadorMiles(",");
        config.setSeparadorDecimales(".");
        config.setDelimitado(d);
        return config;
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("monto negativo produce DEBITO, positivo produce CREDITO")
        void debitoYCredito() {
            byte[] contenido = (
                    "fecha,descripcion,monto\n" +
                    "2026-06-01,Pago proveedor,-150000.00\n" +
                    "2026-06-02,Consignacion,200000.00\n"
            ).getBytes(StandardCharsets.UTF_8);

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(), "2026-06");

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getTipo()).isEqualTo("DEBITO");
            assertThat(resultado.get(0).getMonto()).isEqualByComparingTo("150000.00");
            assertThat(resultado.get(1).getTipo()).isEqualTo("CREDITO");
        }

        @Test
        @DisplayName("columnas debito/credito separadas")
        void columnasSeparadas() {
            ConfiguracionExtractoDetalle config = configBasica();
            config.getDelimitado().setDebitoYCreditoSeparados(true);
            config.getDelimitado().setColumnaDebito(2);
            config.getDelimitado().setColumnaCredito(3);

            byte[] contenido = (
                    "fecha,descripcion,debito,credito\n" +
                    "2026-06-01,Pago,50000,\n" +
                    "2026-06-02,Cobro,,70000\n"
            ).getBytes(StandardCharsets.UTF_8);

            List<Movimiento> resultado = parser.parsear(contenido, config, "2026-06");

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getTipo()).isEqualTo("DEBITO");
            assertThat(resultado.get(1).getTipo()).isEqualTo("CREDITO");
        }

        @Test
        @DisplayName("columna literal tipo_movimiento (formato generico estandar) con monto sin signo")
        void columnaTipoMovimientoLiteral() {
            ConfiguracionExtractoDetalle config = configBasica();
            config.getDelimitado().setColumnaTipoMovimiento(3);

            byte[] contenido = (
                    "fecha,descripcion,monto,tipo_movimiento\n" +
                    "2026-06-01,Pago proveedor,150000.00,DEBITO\n" +
                    "2026-06-02,Consignacion cliente,200000.00,CREDITO\n"
            ).getBytes(StandardCharsets.UTF_8);

            List<Movimiento> resultado = parser.parsear(contenido, config, "2026-06");

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getTipo()).isEqualTo("DEBITO");
            assertThat(resultado.get(0).getMonto()).isEqualByComparingTo("150000.00");
            assertThat(resultado.get(1).getTipo()).isEqualTo("CREDITO");
        }

        @Test
        @DisplayName("fila vacia y montos cero se ignoran")
        void filaVaciaYMontoCero() {
            byte[] contenido = (
                    "fecha,descripcion,monto\n" +
                    "2026-06-01,Pago,100000\n" +
                    "\n" +
                    "2026-06-02,Total,0\n"
            ).getBytes(StandardCharsets.UTF_8);

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(), "2026-06");

            assertThat(resultado).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Extractos sin movimientos")
    class SinMovimientos {

        @Test
        @DisplayName("archivo solo con encabezado retorna lista vacia sin lanzar excepcion")
        void archivoVacioNoLanzaExcepcion() {
            byte[] contenido = "fecha,descripcion,monto\n".getBytes(StandardCharsets.UTF_8);

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(), "2026-06");

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cuadre")
    class Cuadre {

        @Test
        @DisplayName("valida cuadre buscando etiquetas en el texto crudo")
        void validaCuadre() {
            ConfiguracionExtractoDetalle config = configBasica();
            ReglaCuadre regla = new ReglaCuadre();
            regla.setHabilitada(true);
            regla.setEtiquetaSaldoAnterior("Saldo Anterior");
            regla.setEtiquetaCreditos("Creditos");
            regla.setEtiquetaDebitos("Debitos");
            regla.setEtiquetaSaldoFinal("Saldo Final");
            config.setCuadre(regla);

            byte[] contenido = (
                    "Saldo Anterior 100.00\n" +
                    "Creditos 50.00\n" +
                    "Debitos 10.00\n" +
                    "Saldo Final 140.00\n" +
                    "fecha,descripcion,monto\n"
            ).getBytes(StandardCharsets.UTF_8);

            assertThat(parser.validarCuadre(contenido, config).isCuadra()).isTrue();
        }
    }
}
