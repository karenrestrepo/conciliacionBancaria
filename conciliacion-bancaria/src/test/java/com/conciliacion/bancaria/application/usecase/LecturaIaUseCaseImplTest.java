package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.adapter.out.archivo.LectorArchivoAdapter;
import com.conciliacion.bancaria.adapter.out.ia.LectorSimuladoAdapter;
import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.MapeoColumnas;
import com.conciliacion.bancaria.domain.model.MapeoExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoLectura;
import com.conciliacion.bancaria.domain.model.ResultadoLectura.Estado;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.LectorExtractoIaPort;
import com.conciliacion.bancaria.domain.port.out.MapeadorExtractoIaPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.service.InterpreteAuxiliar;
import com.conciliacion.bancaria.domain.service.InterpreteExtracto;
import com.conciliacion.bancaria.domain.service.MuestraTabla;
import com.conciliacion.bancaria.domain.service.RevisorLecturaExtracto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LecturaIaUseCase — carga automática, reintento y confirmación")
class LecturaIaUseCaseImplTest {

    private static final byte[] PDF = "%PDF-1.4 ejemplo".getBytes();

    private final LectorSimuladoAdapter simulado = new LectorSimuladoAdapter();
    private final LectorExtractoIaPort lector = mock(LectorExtractoIaPort.class);
    private final MapeadorExtractoIaPort mapeador = mock(MapeadorExtractoIaPort.class);
    private final ConciliacionRepositoryPort conciliacionRepo = mock(ConciliacionRepositoryPort.class);
    private final MovimientoRepositoryPort movimientoRepo = mock(MovimientoRepositoryPort.class);
    private final CargaCsvUseCase carga = mock(CargaCsvUseCase.class);
    private final LecturaIaUseCaseImpl useCase = useCaseCon(lector);

    private LecturaIaUseCaseImpl useCaseCon(LectorExtractoIaPort lectorUsado) {
        RevisorLecturaExtracto revisor = new RevisorLecturaExtracto();
        return new LecturaIaUseCaseImpl(new LectorArchivoAdapter(), lectorUsado, mapeador, new MuestraTabla(),
                new InterpreteExtracto(new InterpreteAuxiliar(), revisor), revisor,
                conciliacionRepo, movimientoRepo, carga);
    }

    /** Datos ficticios que cuadran. */
    private LecturaExtracto correcta() {
        return simulado.leer(null, "2026-12");
    }

    private LecturaExtracto descuadrada() {
        LecturaExtracto lectura = correcta();
        lectura.setSaldoFinal(BigDecimal.ONE);
        return lectura;
    }

    @BeforeEach
    void conciliacionExistente() {
        // Objeto real: Mockito no puede simular clases concretas en Java 25.
        Conciliacion conciliacion = Conciliacion.builder().id(1L).periodo("2026-12").build();
        when(conciliacionRepo.buscarPorId(1L)).thenReturn(Optional.of(conciliacion));
    }

    @Test
    @DisplayName("caso normal: si cuadra se carga sin pedir revisión")
    void cargaAutomatica() {
        when(lector.leer(any(), any())).thenReturn(correcta());
        when(carga.confirmarExtractoLeido(eq(1L), anyList())).thenReturn("job-1");

        var resultado = useCase.procesar(1L, PDF);

        assertThat(resultado.getEstado()).isEqualTo(Estado.CARGADO);
        assertThat(resultado.getJobId()).isEqualTo("job-1");
    }

    @Test
    @DisplayName("si no cuadra reintenta con el respaldo y carga la lectura buena")
    void reintentoCorrige() {
        when(lector.leer(any(), any())).thenReturn(descuadrada());
        when(lector.tieneRespaldo()).thenReturn(true);
        when(lector.leerConRespaldo(any(), any())).thenReturn(correcta());

        assertThat(useCase.procesar(1L, PDF).getEstado()).isEqualTo(Estado.CARGADO);
    }

    @Test
    @DisplayName("si sigue sin cuadrar no se guarda nada")
    void noCuadraSeRechaza() {
        when(lector.leer(any(), any())).thenReturn(descuadrada());
        when(lector.tieneRespaldo()).thenReturn(true);
        when(lector.leerConRespaldo(any(), any())).thenReturn(descuadrada());

        assertThat(useCase.procesar(1L, PDF).getEstado()).isEqualTo(Estado.RECHAZADO);
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("falla de la herramienta en el reintento: se informa la primera lectura")
    void reintentoFalla() {
        when(lector.leer(any(), any())).thenReturn(descuadrada());
        when(lector.tieneRespaldo()).thenReturn(true);
        when(lector.leerConRespaldo(any(), any())).thenThrow(new CsvValidationException("sin servicio"));

        assertThat(useCase.procesar(1L, PDF).getEstado()).isEqualTo(Estado.RECHAZADO);
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("datos faltantes: sin saldo final pide confirmación")
    void sinSaldos() {
        LecturaExtracto lectura = correcta();
        lectura.setSaldoFinal(null);
        when(lector.leer(any(), any())).thenReturn(lectura);

        assertThat(useCase.procesar(1L, PDF).getEstado()).isEqualTo(Estado.REQUIERE_CONFIRMACION);
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("un extracto de otro mes no se carga solo")
    void otroPeriodo() {
        when(lector.leer(any(), any())).thenReturn(simulado.leer(null, "2026-11"));

        assertThat(useCase.procesar(1L, PDF).getEstado()).isEqualTo(Estado.REQUIERE_CONFIRMACION);
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("mensaje repetido: un extracto ya cargado en tarjetas conjuntas no se agrega otra vez")
    void repetido() {
        LecturaExtracto lectura = correcta();
        List<Movimiento> guardados = lectura.getMovimientos().stream()
                .map(m -> Movimiento.builder()
                        .fecha(LocalDate.parse(m.getFecha()))
                        .descripcion(m.getDescripcion())
                        .monto(m.getMonto().setScale(2))
                        .tipo(m.getTipo())
                        .build())
                .toList();
        when(lector.leer(any(), any())).thenReturn(lectura);
        when(conciliacionRepo.esAuxiliarConjunto(1L)).thenReturn(true);
        when(movimientoRepo.buscarTodosBancariosPorConciliacion(1L)).thenReturn(guardados);

        assertThat(useCase.procesar(1L, PDF).getEstado()).isEqualTo(Estado.RECHAZADO);
        assertThatThrownBy(() -> useCase.confirmar(1L, lectura, false))
                .isInstanceOf(CsvValidationException.class);
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("los datos simulados nunca se cargan solos")
    void simuladoPideConfirmacion() {
        var resultado = useCaseCon(simulado).procesar(1L, PDF);

        assertThat(resultado.getEstado()).isEqualTo(Estado.REQUIERE_CONFIRMACION);
        assertThat(resultado.getRevision().getAdvertencias()).anyMatch(a -> a.startsWith("DATOS SIMULADOS"));
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("un archivo de un tipo desconocido se rechaza sin usar la IA")
    void tipoDesconocido() {
        assertThatThrownBy(() -> useCase.procesar(1L, new byte[]{1, 0, 2, 0}))
                .isInstanceOf(CsvValidationException.class);
        verifyNoInteractions(lector, mapeador, carga);
    }

    private static final byte[] CSV = """
            ;;SALDO ANTERIOR;1.000,00
            FECHA;REFERENCIA;DETALLE;VALOR
            05/12;A1;ABONO EJEMPLO;500,00
            10/12;B2;PAGO EJEMPLO;200,00-
            ;;SALDO FINAL;1.300,00
            """.getBytes();

    private static MapeoExtracto.MapeoExtractoBuilder mapeo() {
        return MapeoExtracto.builder()
                .tipoProducto("CUENTA").hoja(0).primeraFilaDatos(2)
                .columnaFecha(0).columnasDescripcion(List.of(2, 1))
                .esquemaMonto("MONTO_CON_SIGNO").columnaMonto(3).positivoEsAbono(true)
                .separadorDecimal(",").ordenFecha("DMA")
                .saldoAnterior(new MapeoColumnas.Posicion(0, 3))
                .saldoFinal(new MapeoColumnas.Posicion(4, 3));
    }

    @Test
    @DisplayName("un CSV que cuadra se carga solo, completando el año de las fechas")
    @SuppressWarnings("unchecked")
    void tablaCuadra() {
        when(mapeador.mapear(anyString(), eq("2026-12"))).thenReturn(mapeo().build());
        when(carga.confirmarExtractoLeido(eq(1L), anyList())).thenReturn("job-1");

        assertThat(useCase.procesar(1L, CSV).getEstado()).isEqualTo(Estado.CARGADO);

        ArgumentCaptor<List<Movimiento>> captor = ArgumentCaptor.forClass(List.class);
        verify(carga).confirmarExtractoLeido(eq(1L), captor.capture());
        assertThat(captor.getValue()).extracting(Movimiento::getTipo).containsExactly("CREDITO", "DEBITO");
        assertThat(captor.getValue().get(0).getFecha()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(captor.getValue().get(0).getDescripcion()).isEqualTo("ABONO EJEMPLO — A1");
        verifyNoInteractions(lector);
    }

    @Test
    @DisplayName("el encabezado repetido en cada página no impide cargar el extracto")
    void encabezadoRepetido() {
        byte[] csv = """
                ;;SALDO ANTERIOR;1.000,00
                FECHA;REFERENCIA;DETALLE;VALOR
                05/12;A1;ABONO EJEMPLO;500,00
                DESDE;HASTA;TIPO;SUCURSAL
                2026/11/30;2026/12/31;CUENTA;SUCURSAL DE EJEMPLO
                10/12;B2;PAGO EJEMPLO;200,00-
                ;;SALDO FINAL;1.300,00
                """.getBytes();
        when(mapeador.mapear(anyString(), anyString()))
                .thenReturn(mapeo().saldoFinal(new MapeoColumnas.Posicion(6, 3)).build());
        when(carga.confirmarExtractoLeido(eq(1L), anyList())).thenReturn("job-1");

        ResultadoLectura resultado = useCase.procesar(1L, csv);

        assertThat(resultado.getEstado()).isEqualTo(Estado.CARGADO);
        assertThat(resultado.getRevision().getLectura().getMovimientos()).hasSize(2);
    }

    @Test
    @DisplayName("una tabla mal interpretada se reintenta y, si sigue sin cuadrar, no se carga")
    void tablaNoCuadra() {
        MapeoExtracto invertido = mapeo().positivoEsAbono(false).build();
        when(mapeador.mapear(anyString(), anyString())).thenReturn(invertido);
        when(mapeador.tieneRespaldo()).thenReturn(true);
        when(mapeador.mapearConRespaldo(anyString(), anyString())).thenReturn(invertido);

        assertThat(useCase.procesar(1L, CSV).getEstado()).isEqualTo(Estado.RECHAZADO);
        verify(mapeador).mapearConRespaldo(anyString(), anyString());
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("datos ambiguos: una tarjeta en tabla con columnas de abonos y cargos usa el saldo como deuda")
    void tarjetaEnTabla() {
        byte[] csv = "100;;\n2026-12-03;COMPRA;50;\n2026-12-04;PAGO;;20\n130;;\n".getBytes();
        when(mapeador.mapear(anyString(), anyString())).thenReturn(MapeoExtracto.builder()
                .tipoProducto("TARJETA_CREDITO").ultimosDigitosTarjeta("1234").hoja(0).primeraFilaDatos(1)
                .columnaFecha(0).columnasDescripcion(List.of(1))
                .esquemaMonto("ABONO_CARGO").columnaCargos(2).columnaAbonos(3)
                .separadorDecimal(".").ordenFecha("AMD")
                .saldoAnterior(new MapeoColumnas.Posicion(0, 0)).saldoFinal(new MapeoColumnas.Posicion(3, 0))
                .build());
        when(carga.confirmarExtractoLeido(eq(1L), anyList())).thenReturn("job-1");

        assertThat(useCase.procesar(1L, csv).getEstado()).isEqualTo(Estado.CARGADO);
    }

    @Test
    @DisplayName("una foto la lee la IA como documento")
    void foto() {
        when(lector.leer(any(), eq("2026-12"))).thenReturn(correcta());
        when(carga.confirmarExtractoLeido(eq(1L), anyList())).thenReturn("job-1");

        assertThat(useCase.procesar(1L, new byte[]{(byte) 0x89, 'P', 'N', 'G'}).getEstado())
                .isEqualTo(Estado.CARGADO);
        verifyNoInteractions(mapeador);
    }

    @Test
    @DisplayName("una lectura que no cuadra no se guarda, ni siquiera confirmando")
    void noCuadraNoGuarda() {
        assertThatThrownBy(() -> useCase.confirmar(1L, descuadrada(), true))
                .isInstanceOf(CsvValidationException.class);
        verifyNoInteractions(carga);
    }

    @Test
    @DisplayName("sin saldos solo se guarda si el usuario lo confirma")
    void confirmacion() {
        LecturaExtracto lectura = correcta();
        lectura.setSaldoFinal(null);

        assertThatThrownBy(() -> useCase.confirmar(1L, lectura, false))
                .isInstanceOf(CsvValidationException.class);
        useCase.confirmar(1L, lectura, true);
        verify(carga).confirmarExtractoLeido(eq(1L), anyList());
    }
}
