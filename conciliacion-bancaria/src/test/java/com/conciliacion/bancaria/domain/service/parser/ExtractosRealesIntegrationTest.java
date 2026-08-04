package com.conciliacion.bancaria.domain.service.parser;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoCuadre;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;
import com.conciliacion.bancaria.domain.service.parser.support.ContinuacionLineaHelper;
import com.conciliacion.bancaria.domain.service.parser.support.CuadreValidator;
import com.conciliacion.bancaria.domain.service.parser.support.FechaResolver;
import com.conciliacion.bancaria.domain.service.parser.support.LineasTextoReader;
import com.conciliacion.bancaria.domain.service.parser.support.MontoParser;
import com.conciliacion.bancaria.domain.service.parser.support.SignoResolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Prueba de aceptación del motor genérico: para cada uno de los 5 extractos
 * bancarios reales adjuntados por el usuario (fixtures versionadas en
 * src/test/resources/extractos-muestra), se arma una configuración (fixtures
 * en src/test/resources/extractos-config) usando SOLO el motor nuevo — sin
 * ningún parser ni código a medida por banco — y se verifica que el parseo no
 * falle y que el cuadre (cuando aplica) coincida.
 */
@DisplayName("Motor genérico contra los 5 extractos reales de muestra")
class ExtractosRealesIntegrationTest {

    private BankStatementParserDispatcher dispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MontoParser montoParser = new MontoParser();
        CuadreValidator cuadreValidator = new CuadreValidator(montoParser);
        dispatcher = new BankStatementParserDispatcher(
                new DelimitadoBankStatementParser(montoParser, new FechaResolver(), new SignoResolver(), cuadreValidator, new LineasTextoReader()),
                new AnchoFijoBankStatementParser(montoParser, new FechaResolver(), new SignoResolver(),
                        new ContinuacionLineaHelper(), cuadreValidator, new LineasTextoReader()),
                new ExcelBankStatementParser(montoParser, new FechaResolver(), new ContinuacionLineaHelper(), cuadreValidator));
    }

    private byte[] leerMuestra(String nombre) throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("extractos-muestra/" + nombre)) {
            assertThat(in).as("fixture " + nombre).isNotNull();
            return in.readAllBytes();
        }
    }

    private ConfiguracionExtractoDetalle leerConfig(String nombre) throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("extractos-config/" + nombre)) {
            assertThat(in).as("config " + nombre).isNotNull();
            return objectMapper.readValue(in, ConfiguracionExtractoDetalle.class);
        }
    }

    @Test
    @DisplayName("A. Davivienda cuenta de ahorro: parsea movimientos y cuadra")
    void daviviendaAhorro() throws IOException {
        byte[] archivo = leerMuestra("davivienda-ahorro.txt");
        ConfiguracionExtractoDetalle config = leerConfig("davivienda-ahorro.json");

        List<Movimiento> movimientos = dispatcher.parsear(archivo, config, "2026-06");
        ResultadoCuadre cuadre = dispatcher.validarCuadre(archivo, config);

        assertThat(movimientos).isNotEmpty();
        assertThat(cuadre.isCuadra()).as("advertencias: %s", cuadre.getAdvertencias()).isTrue();
        // Extracto de cuenta de ahorro, sin tarjeta -- no debe inventar dígitos.
        assertThat(movimientos).allMatch(m -> m.getUltimosDigitosTarjeta() == null);
    }

    @Test
    @DisplayName("B. Davivienda cuenta corriente: parsea movimientos con continuacion multilinea y cuadra")
    void daviviendaCorriente() throws IOException {
        byte[] archivo = leerMuestra("davivienda-corriente.txt");
        ConfiguracionExtractoDetalle config = leerConfig("davivienda-corriente.json");

        List<Movimiento> movimientos = dispatcher.parsear(archivo, config, "2026-06");
        ResultadoCuadre cuadre = dispatcher.validarCuadre(archivo, config);

        assertThat(movimientos).isNotEmpty();
        // La descripción del primer movimiento con continuación debe traer el texto de la 2a línea física.
        assertThat(movimientos.stream().anyMatch(m ->
                m.getDescripcion().contains("900700192") && m.getDescripcion().contains("fc43188a43464proindus")))
                .as("al menos un movimiento debe incluir el texto de su línea de continuación")
                .isTrue();
        assertThat(cuadre.isCuadra()).as("advertencias: %s", cuadre.getAdvertencias()).isTrue();
        assertThat(movimientos).allMatch(m -> m.getUltimosDigitosTarjeta() == null);
    }

    @Test
    @DisplayName("C. Davivienda tarjeta de credito: extracto sin movimientos ese periodo no lanza excepcion")
    void daviviendaTarjetaSinMovimientos() throws IOException {
        byte[] archivo = leerMuestra("davivienda-tarjeta.txt");
        ConfiguracionExtractoDetalle config = leerConfig("davivienda-tarjeta.json");

        assertThatCode(() -> dispatcher.parsear(archivo, config, "2026-06")).doesNotThrowAnyException();
        List<Movimiento> movimientos = dispatcher.parsear(archivo, config, "2026-06");
        assertThat(movimientos).isEmpty();
    }

    @Test
    @DisplayName("C2. Davivienda tarjeta de credito con movimientos reales: invertir=true asigna DEBITO a cargos y CREDITO a pagos")
    void daviviendaTarjetaConMovimientos() throws IOException {
        byte[] archivo = leerMuestra("davivienda-tarjeta-con-movimientos.txt");
        ConfiguracionExtractoDetalle config = leerConfig("davivienda-tarjeta-con-movimientos.json");

        List<Movimiento> movimientos = dispatcher.parsear(archivo, config, "2026-05");

        assertThat(movimientos).hasSize(10);
        assertThat(movimientos).allMatch(m -> !m.getDescripcion().isBlank());
        // Cargos ("+") deben quedar como DEBITO tras invertir; pagos ("-") como CREDITO.
        assertThat(movimientos.stream().filter(m -> m.getDescripcion().contains("PAGO")))
                .as("los pagos deben quedar como CREDITO")
                .allMatch(m -> m.getTipo().equals("CREDITO"));
        assertThat(movimientos.stream().filter(m -> m.getDescripcion().contains("IMP 4XMIL")))
                .as("los cargos (impuesto 4x1000) deben quedar como DEBITO")
                .allMatch(m -> m.getTipo().equals("DEBITO"));
        // El encabezado trae "#  5474 8200 4603 5264" -- todo movimiento de este archivo
        // debe quedar etiquetado con los últimos 4 dígitos de ESA tarjeta.
        assertThat(movimientos).allMatch(m -> "5264".equals(m.getUltimosDigitosTarjeta()));
    }

    @Test
    @DisplayName("D. Davivienda fondo fiduciario: extracto sin movimientos ese periodo no lanza excepcion, cuadra por saldo")
    void daviviendaFondoSinMovimientos() throws IOException {
        byte[] archivo = leerMuestra("davivienda-fondo.txt");
        ConfiguracionExtractoDetalle config = leerConfig("davivienda-fondo.json");

        List<Movimiento> movimientos = dispatcher.parsear(archivo, config, "2026-06");
        ResultadoCuadre cuadre = dispatcher.validarCuadre(archivo, config);

        assertThat(movimientos).isEmpty();
        assertThat(cuadre.isCuadra()).as("advertencias: %s", cuadre.getAdvertencias()).isTrue();
    }

    @Test
    @DisplayName("E. Excel de otro banco: bloques de encabezado repetidos por pagina se descartan y cuadra")
    void excelOtroBanco() throws IOException {
        byte[] archivo = leerMuestra("banco-otro-corriente.xlsx");
        ConfiguracionExtractoDetalle config = leerConfig("banco-otro-corriente.json");

        List<Movimiento> movimientos = dispatcher.parsear(archivo, config, "2026-06");
        ResultadoCuadre cuadre = dispatcher.validarCuadre(archivo, config);

        assertThat(movimientos).isNotEmpty();
        assertThat(cuadre.isCuadra()).as("advertencias: %s", cuadre.getAdvertencias()).isTrue();
    }
}
