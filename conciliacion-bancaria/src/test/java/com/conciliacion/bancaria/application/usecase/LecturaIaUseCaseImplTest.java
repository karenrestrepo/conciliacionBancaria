package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.adapter.out.ia.LectorSimuladoAdapter;
import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoLectura.Estado;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.LectorExtractoIaPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.service.RevisorLecturaExtracto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    private final ConciliacionRepositoryPort conciliacionRepo = mock(ConciliacionRepositoryPort.class);
    private final MovimientoRepositoryPort movimientoRepo = mock(MovimientoRepositoryPort.class);
    private final CargaCsvUseCase carga = mock(CargaCsvUseCase.class);
    private final LecturaIaUseCaseImpl useCase = useCaseCon(lector);

    private LecturaIaUseCaseImpl useCaseCon(LectorExtractoIaPort lectorUsado) {
        return new LecturaIaUseCaseImpl(lectorUsado, new RevisorLecturaExtracto(),
                conciliacionRepo, movimientoRepo, carga);
    }

    /** Datos ficticios que cuadran. */
    private LecturaExtracto correcta() {
        return simulado.leer(new byte[0], "2026-12");
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
        when(lector.leer(any(), any())).thenReturn(simulado.leer(new byte[0], "2026-11"));

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
    @DisplayName("un archivo que no es PDF se rechaza")
    void noEsPdf() {
        assertThatThrownBy(() -> useCase.procesar(1L, "hola mundo".getBytes()))
                .isInstanceOf(CsvValidationException.class);
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
