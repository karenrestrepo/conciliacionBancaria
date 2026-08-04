package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.extractoconfig.ColumnaPosicion;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfigAnchoFijo;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConvencionSigno;
import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaContinuacion;
import com.conciliacion.bancaria.domain.model.extractoconfig.ReglaCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.TipoOrigenExtracto;
import com.conciliacion.bancaria.domain.service.parser.support.ContinuacionLineaHelper;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.LineasTextoReader;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.domain.service.parser.support.SignoResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnchoFijoBankStatementParser — reemplaza la regex Java del parser TXT anterior")
class AnchoFijoBankStatementParserTest {

    // Posiciones (1-based, inclusive) usadas por todos los tests de esta clase.
    private static final int DIA_INI = 1, DIA_FIN = 2;
    private static final int MES_INI = 4, MES_FIN = 5;
    private static final int DESC_INI = 8, DESC_FIN = 40;
    private static final int MONTO_INI = 42, MONTO_FIN = 58;
    private static final int OFICINA_INI = 60, OFICINA_FIN = 75;

    private AnchoFijoBankStatementParser parser;

    @BeforeEach
    void setUp() {
        parser = new AnchoFijoBankStatementParser(
                new MontoParser(), new FechaResolver(), new SignoResolver(),
                new ContinuacionLineaHelper(), new CuadreValidator(new MontoParser()),
                new LineasTextoReader());
    }

    private String linea(String dia, String mes, String descripcion, String monto, String oficina) {
        StringBuilder sb = new StringBuilder(" ".repeat(80));
        poner(sb, dia, DIA_INI, DIA_FIN);
        poner(sb, mes, MES_INI, MES_FIN);
        poner(sb, descripcion, DESC_INI, DESC_FIN);
        poner(sb, monto, MONTO_INI, MONTO_FIN);
        poner(sb, oficina, OFICINA_INI, OFICINA_FIN);
        return sb.toString();
    }

    private void poner(StringBuilder sb, String texto, int inicio, int fin) {
        if (texto == null || texto.isEmpty()) return;
        int i = inicio - 1;
        for (int k = 0; k < texto.length() && i + k < sb.length(); k++) {
            sb.setCharAt(i + k, texto.charAt(k));
        }
    }

    private ConfiguracionExtractoDetalle configBasica(ReglaContinuacion continuacion) {
        List<ColumnaPosicion> columnas = new ArrayList<>();
        columnas.add(new ColumnaPosicion("dia", DIA_INI, DIA_FIN));
        columnas.add(new ColumnaPosicion("mes", MES_INI, MES_FIN));
        columnas.add(new ColumnaPosicion("descripcion", DESC_INI, DESC_FIN));
        columnas.add(new ColumnaPosicion("monto", MONTO_INI, MONTO_FIN));
        columnas.add(new ColumnaPosicion("oficina", OFICINA_INI, OFICINA_FIN));

        ConfigAnchoFijo anchoFijo = new ConfigAnchoFijo();
        anchoFijo.setColumnas(columnas);
        anchoFijo.setConvencionSigno(ConvencionSigno.SUFIJO);

        ConfiguracionExtractoDetalle config = new ConfiguracionExtractoDetalle();
        config.setTipoOrigen(TipoOrigenExtracto.ANCHO_FIJO);
        config.setEncoding("UTF-8");
        config.setSeparadorMiles(",");
        config.setSeparadorDecimales(".");
        config.setAnchoFijo(anchoFijo);
        if (continuacion != null) config.setContinuacion(continuacion);
        return config;
    }

    private byte[] archivo(String... lineas) {
        return String.join("\n", lineas).getBytes(StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("fecha por dia+mes con anio del periodo, signo sufijo")
        void fechaDiaMesYSignoSufijo() {
            byte[] contenido = archivo(
                    linea("01", "06", "Abono ACH BANCOLOMBIA", "800,670.00+", "PROCESOS ACH"),
                    linea("02", "06", "Descuento Nomina", "1,520,000.00-", "PORTAL PYMES")
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(null), "2026-06");

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getFecha()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(resultado.get(0).getTipo()).isEqualTo("CREDITO");
            assertThat(resultado.get(0).getMonto()).isEqualByComparingTo("800670.00");
            assertThat(resultado.get(1).getTipo()).isEqualTo("DEBITO");
        }

        @Test
        @DisplayName("lineas en blanco de relleno se descartan silenciosamente")
        void lineasEnBlancoSeDescartan() {
            byte[] contenido = archivo(
                    linea("01", "06", "Abono", "100.00+", ""),
                    "",
                    "   ",
                    linea("02", "06", "Cobro", "50.00+", "")
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(null), "2026-06");

            assertThat(resultado).hasSize(2);
        }

        @Test
        @DisplayName("invertir=true voltea DEBITO/CREDITO (tarjeta de credito: '+' es cargo, '-' es pago)")
        void invertirVolteaTipo() {
            ConfiguracionExtractoDetalle config = configBasica(null);
            config.getAnchoFijo().setInvertir(true);

            byte[] contenido = archivo(
                    linea("04", "05", "IMP 4XMIL", "114+", ""),
                    linea("20", "05", "PAGO DEBITO AUT", "883,734-", "")
            );

            List<Movimiento> resultado = parser.parsear(contenido, config, "2026-05");

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getTipo()).isEqualTo("DEBITO");
            assertThat(resultado.get(1).getTipo()).isEqualTo("CREDITO");
        }
    }

    @Nested
    @DisplayName("Transacciones multilinea")
    class Multilinea {

        private ReglaContinuacion reglaContinuacion() {
            ReglaContinuacion r = new ReglaContinuacion();
            r.setHabilitada(true);
            r.setCampoAncla("dia");
            r.setCampoDestino("descripcion");
            return r;
        }

        @Test
        @DisplayName("linea de continuacion (solo descripcion) se concatena al movimiento anterior")
        void continuacionSeConcatena() {
            byte[] contenido = archivo(
                    linea("01", "06", "Abono ACH BANCOLOMBIA 900700192", "800,670.00+", "PROCESOS ACH"),
                    linea(null, null, "fc43188a43464proindus AS", null, null)
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(reglaContinuacion()), "2026-06");

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getDescripcion())
                    .contains("Abono ACH BANCOLOMBIA 900700192")
                    .contains("fc43188a43464proindus AS");
        }

        @Test
        @DisplayName("encabezado de tabla repetido (fecha vacia PERO otras columnas con texto) NO se cuela como continuacion")
        void encabezadoRepetidoNoSeCuela() {
            byte[] contenido = archivo(
                    linea("01", "06", "Abono ACH BANCOLOMBIA 900700192", "800,670.00+", "PROCESOS ACH"),
                    // Encabezado repetido de página: fecha vacía, pero "Oficina" trae texto.
                    linea(null, null, "Clase de Movimiento", null, "Oficina"),
                    linea("02", "06", "Cobro", "50.00+", "PROCESOS ACH")
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(reglaContinuacion()), "2026-06");

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getDescripcion())
                    .doesNotContain("Clase de Movimiento");
        }
    }

    @Nested
    @DisplayName("Extractos sin movimientos")
    class SinMovimientos {

        @Test
        @DisplayName("extracto sin transacciones (ej. tarjeta de credito en 0) retorna lista vacia sin lanzar excepcion")
        void extractoVacioNoLanzaExcepcion() {
            byte[] contenido = archivo(
                    "        MOVIMIENTOS",
                    "  Documento  Fecha   Descripcion   Valor   Saldo Pendiente",
                    ""
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(null), "2026-06");

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("Número de tarjeta en el encabezado")
    class NumeroTarjeta {

        @Test
        @DisplayName("sin 'Tarjeta de Cr.dito' en el archivo, ultimosDigitosTarjeta queda null (cuenta bancaria normal)")
        void sinEncabezadoDeTarjetaQuedaNull() {
            byte[] contenido = archivo(
                    "NIT. 860.034.313-7          Extracto Cuenta de Ahorros",
                    linea("01", "06", "Abono ACH", "100.00+", "")
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(null), "2026-06");

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getUltimosDigitosTarjeta()).isNull();
        }

        @Test
        @DisplayName("con el encabezado real (acento reemplazado por '?', como vienen los archivos reales), "
                + "todos los movimientos quedan etiquetados con los últimos 4 dígitos")
        void conEncabezadoRealEtiquetaLosUltimos4Digitos() {
            byte[] contenido = archivo(
                    "                    NIT. 860.034.313-7          Tarjeta de Cr?dito",
                    "",
                    "                                       #  5474 8200 0465 4924",
                    "",
                    linea("01", "06", "Compra en linea", "100.00+", ""),
                    linea("02", "06", "Pago tarjeta", "50.00-", "")
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(null), "2026-06");

            assertThat(resultado).hasSize(2);
            assertThat(resultado).allMatch(m -> "4924".equals(m.getUltimosDigitosTarjeta()));
        }

        @Test
        @DisplayName("con el acento bien formado (é en vez de '?') también reconoce el patrón")
        void conAcentoBienFormadoTambienFunciona() {
            byte[] contenido = archivo(
                    "                    NIT. 860.034.313-7          Tarjeta de Crédito",
                    "",
                    "                                       #  5474 8200 4603 5264",
                    "",
                    linea("01", "06", "Compra", "100.00+", "")
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(null), "2026-06");

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getUltimosDigitosTarjeta()).isEqualTo("5264");
        }

        @Test
        @DisplayName("extracto sin movimientos igual permite reconocer el patrón sin lanzar nada (lista vacía)")
        void sinMovimientosNoLanzaAunqueHayaEncabezadoDeTarjeta() {
            byte[] contenido = archivo(
                    "Tarjeta de Cr?dito",
                    "#  5474 8200 0465 4924",
                    "        MOVIMIENTOS",
                    ""
            );

            List<Movimiento> resultado = parser.parsear(contenido, configBasica(null), "2026-06");

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cuadre")
    class Cuadre {

        @Test
        @DisplayName("cuadre correcto con separadores estilo Davivienda (coma miles, punto decimal)")
        void cuadreCorrecto() {
            ConfiguracionExtractoDetalle config = configBasica(null);
            ReglaCuadre regla = new ReglaCuadre();
            regla.setHabilitada(true);
            regla.setEtiquetaSaldoAnterior("Saldo Anterior");
            regla.setEtiquetaCreditos("Más:Créditos");
            regla.setEtiquetaDebitos("Menos: Débitos");
            regla.setEtiquetaSaldoFinal("Nuevo Saldo");
            config.setCuadre(regla);

            byte[] contenido = archivo(
                    "Saldo Anterior $80,568,399.90",
                    "Más:Créditos $166,072,718.00",
                    "Menos: Débitos $146,832,168.67",
                    "Nuevo Saldo $99,808,949.23"
            );

            assertThat(parser.validarCuadre(contenido, config).isCuadra()).isTrue();
        }
    }
}
