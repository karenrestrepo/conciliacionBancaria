package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.model.ArchivoAuxiliar;
import com.conciliacion.bancaria.domain.model.AuxiliarLeido;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.MapeoAuxiliar;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResultadoAuxiliar;
import com.conciliacion.bancaria.domain.model.ResultadoLectura.Estado;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;
import com.conciliacion.bancaria.domain.model.TablaArchivo;
import com.conciliacion.bancaria.domain.model.TablaArchivo.Celda;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.LectorArchivoPort;
import com.conciliacion.bancaria.domain.port.out.LectorDocumentoAuxiliarIaPort;
import com.conciliacion.bancaria.domain.port.out.MapeadorAuxiliarIaPort;
import com.conciliacion.bancaria.domain.port.out.MovimientoRepositoryPort;
import com.conciliacion.bancaria.domain.service.InterpreteAuxiliar;
import com.conciliacion.bancaria.domain.service.MuestraTabla;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("LecturaAuxiliarIaUseCaseImpl — decide si un auxiliar leído se puede cargar")
class LecturaAuxiliarIaUseCaseImplTest {

    private static final byte[] ARCHIVO = {1};

    private final LectorArchivoPort lectorArchivo = mock(LectorArchivoPort.class);
    private final LectorDocumentoAuxiliarIaPort lectorDocumento = mock(LectorDocumentoAuxiliarIaPort.class);
    private final MapeadorAuxiliarIaPort mapeador = mock(MapeadorAuxiliarIaPort.class);
    private final ConciliacionRepositoryPort conciliacionRepo = mock(ConciliacionRepositoryPort.class);
    private final MovimientoRepositoryPort movimientoRepo = mock(MovimientoRepositoryPort.class);
    private final CargaCsvUseCase carga = mock(CargaCsvUseCase.class);
    private final LecturaAuxiliarIaUseCaseImpl useCase = new LecturaAuxiliarIaUseCaseImpl(
            lectorArchivo, mapeador, lectorDocumento, new MuestraTabla(), new InterpreteAuxiliar(),
            conciliacionRepo, movimientoRepo, carga);

    @BeforeEach
    void preparar() {
        when(conciliacionRepo.buscarPorId(1L))
                .thenReturn(Optional.of(Conciliacion.builder().id(1L).periodo("2026-12").build()));
        when(lectorArchivo.leer(ARCHIVO)).thenReturn(ArchivoAuxiliar.deTabla(tabla(
                "Fecha|Documento|Detalle|Débito|Crédito",
                "01/12/2026|RC-1|Abono cliente|100.00|",
                "02/12/2026|CE-7|Pago proveedor||40.00",
                "||TOTALES|100.00|40.00")));
        when(movimientoRepo.buscarTodosContablesPorConciliacion(1L)).thenReturn(List.of());
        when(carga.cargarAuxiliarLeido(eq(1L), anyList()))
                .thenReturn(ResumenCargaAuxiliar.builder().jobId("job-1").nuevos(2).build());
    }

    private static TablaArchivo tabla(String... filas) {
        return new TablaArchivo(List.of(new TablaArchivo.Hoja("a", Arrays.stream(filas)
                .map(f -> Arrays.stream(f.split("\\|", -1)).map(Celda::deTexto).toList())
                .toList())));
    }

    private static MapeoAuxiliar.MapeoAuxiliarBuilder mapeo() {
        return MapeoAuxiliar.builder()
                .hoja(0).primeraFilaDatos(1)
                .columnaFecha(0).columnaComprobante(1).columnasDescripcion(List.of(2))
                .esquemaMonto("DEBITO_CREDITO").columnaDebito(3).columnaCredito(4)
                .separadorDecimal(".").ordenFecha("DMA");
    }

    private static MapeoAuxiliar conTotales() {
        return mapeo()
                .totalDebitos(new MapeoAuxiliar.Posicion(3, 3))
                .totalCreditos(new MapeoAuxiliar.Posicion(3, 4))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Movimiento> cargados() {
        ArgumentCaptor<List<Movimiento>> captor = ArgumentCaptor.forClass(List.class);
        verify(carga).cargarAuxiliarLeido(eq(1L), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("si cuadra se carga sin intervención")
    void cargaAutomatica() {
        when(mapeador.mapear(anyString())).thenReturn(conTotales());

        ResultadoAuxiliar r = useCase.procesar(1L, ARCHIVO);

        assertThat(r.getEstado()).isEqualTo(Estado.CARGADO);
        assertThat(r.getResumen().getJobId()).isEqualTo("job-1");
        assertThat(cargados()).hasSize(2);
    }

    @Test
    @DisplayName("si no cuadra se reintenta con el modelo de respaldo")
    void reintento() {
        when(mapeador.mapear(anyString())).thenReturn(conTotales().toBuilder().primeraFilaDatos(2).build());
        when(mapeador.tieneRespaldo()).thenReturn(true);
        when(mapeador.mapearConRespaldo(anyString())).thenReturn(conTotales());

        assertThat(useCase.procesar(1L, ARCHIVO).getEstado()).isEqualTo(Estado.CARGADO);
    }

    @Test
    @DisplayName("si sigue sin cuadrar no se carga nada")
    void rechazo() {
        when(mapeador.mapear(anyString())).thenReturn(conTotales().toBuilder().primeraFilaDatos(2).build());

        ResultadoAuxiliar r = useCase.procesar(1L, ARCHIVO);

        assertThat(r.getEstado()).isEqualTo(Estado.RECHAZADO);
        verify(carga, never()).cargarAuxiliarLeido(any(), anyList());
    }

    @Test
    @DisplayName("sin totales pide confirmación y avisa si se pueden anular movimientos")
    void confirmacion() {
        when(mapeador.mapear(anyString())).thenReturn(mapeo().build());
        when(movimientoRepo.buscarTodosContablesPorConciliacion(1L))
                .thenReturn(List.of(guardado("2026-12-09", "9", "DEBITO", "X-1", "otro")));

        ResultadoAuxiliar r = useCase.procesar(1L, ARCHIVO);

        assertThat(r.getEstado()).isEqualTo(Estado.REQUIERE_CONFIRMACION);
        assertThat(r.getRevision().getAdvertencias()).anyMatch(a -> a.contains("se anularán"));
        verify(carga, never()).cargarAuxiliarLeido(any(), anyList());
    }

    @Test
    @DisplayName("confirmar vuelve a leer el archivo sin llamar a la IA")
    void confirmar() {
        assertThat(useCase.confirmar(1L, ARCHIVO, mapeo().build(), null).getJobId()).isEqualTo("job-1");

        verify(mapeador, never()).mapear(anyString());
    }

    @Test
    @DisplayName("confirmar no permite cargar algo que no cuadra")
    void confirmarSinCuadre() {
        assertThatThrownBy(() -> useCase.confirmar(1L, ARCHIVO,
                conTotales().toBuilder().primeraFilaDatos(2).build(), null))
                .isInstanceOf(CsvValidationException.class);
    }

    @Test
    @DisplayName("modo simulado siempre pide confirmación")
    void simulado() {
        when(mapeador.mapear(anyString())).thenReturn(conTotales());
        when(mapeador.esSimulado()).thenReturn(true);

        assertThat(useCase.procesar(1L, ARCHIVO).getEstado()).isEqualTo(Estado.REQUIERE_CONFIRMACION);
    }

    @Test
    @DisplayName("archivo repetido: lo ya guardado conserva su identidad aunque se lea distinto")
    void conservaIdentidad() {
        when(mapeador.mapear(anyString())).thenReturn(conTotales());
        when(movimientoRepo.buscarTodosContablesPorConciliacion(1L)).thenReturn(List.of(
                guardado("2026-12-01", "100", "CREDITO", "RC-1", "descripción anterior"),
                guardado("2026-12-02", "40", "DEBITO", null, "sin comprobante")));

        useCase.procesar(1L, ARCHIVO);

        assertThat(cargados()).extracting(Movimiento::getDescripcion)
                .containsExactly("descripción anterior", "sin comprobante");
        assertThat(cargados()).extracting(Movimiento::getNumeroComprobante).containsExactly("RC-1", null);
    }

    private AuxiliarLeido transcripcion(String totalDebitos) {
        return AuxiliarLeido.builder()
                .totalDebitos(totalDebitos == null ? null : new BigDecimal(totalDebitos))
                .totalCreditos(totalDebitos == null ? null : new BigDecimal("40"))
                .movimientos(List.of(
                        new AuxiliarLeido.Fila("2026-12-01", "RC-1", "Abono", new BigDecimal("100"), BigDecimal.ZERO),
                        new AuxiliarLeido.Fila("2026-12-02", null, "Pago", BigDecimal.ZERO, new BigDecimal("40"))))
                .build();
    }

    private void esUnPdf() {
        when(lectorArchivo.leer(ARCHIVO))
                .thenReturn(ArchivoAuxiliar.deDocumento(ArchivoAuxiliar.Tipo.PDF, ARCHIVO));
    }

    @Test
    @DisplayName("un auxiliar en documento que cuadra se carga solo, con el sentido contable convertido")
    void documentoCuadra() {
        esUnPdf();
        when(lectorDocumento.leer(any(), eq("2026-12"))).thenReturn(transcripcion("100"));

        assertThat(useCase.procesar(1L, ARCHIVO).getEstado()).isEqualTo(Estado.CARGADO);
        assertThat(cargados()).extracting(Movimiento::getTipo).containsExactly("CREDITO", "DEBITO");
        verify(mapeador, never()).mapear(anyString());
    }

    @Test
    @DisplayName("un documento que no cuadra se reintenta y, si sigue igual, no se carga")
    void documentoNoCuadra() {
        esUnPdf();
        when(lectorDocumento.leer(any(), anyString())).thenReturn(transcripcion("999"));
        when(lectorDocumento.tieneRespaldo()).thenReturn(true);
        when(lectorDocumento.leerConRespaldo(any(), anyString())).thenReturn(transcripcion("999"));

        assertThat(useCase.procesar(1L, ARCHIVO).getEstado()).isEqualTo(Estado.RECHAZADO);
        verify(lectorDocumento).leerConRespaldo(any(), anyString());
        verify(carga, never()).cargarAuxiliarLeido(any(), anyList());
    }

    @Test
    @DisplayName("un documento sin totales se confirma con la lectura ya hecha, sin volver a usar la IA")
    void documentoConfirmado() {
        esUnPdf();
        when(lectorDocumento.leer(any(), anyString())).thenReturn(transcripcion(null));

        ResultadoAuxiliar r = useCase.procesar(1L, ARCHIVO);
        assertThat(r.getEstado()).isEqualTo(Estado.REQUIERE_CONFIRMACION);
        useCase.confirmar(1L, ARCHIVO, null, r.getLectura());

        assertThat(cargados()).hasSize(2);
        verify(lectorDocumento, times(1)).leer(any(), anyString());
    }

    @Test
    @DisplayName("datos ambiguos: una fecha ilegible en un documento impide cargarlo")
    void documentoConFechaIlegible() {
        esUnPdf();
        AuxiliarLeido leido = transcripcion("100");
        leido.getMovimientos().get(0).setFecha("??");
        when(lectorDocumento.leer(any(), anyString())).thenReturn(leido);

        assertThat(useCase.procesar(1L, ARCHIVO).getEstado()).isEqualTo(Estado.RECHAZADO);
    }

    private Movimiento guardado(String fecha, String monto, String tipo, String comprobante, String descripcion) {
        return Movimiento.builder()
                .id(5L).fecha(LocalDate.parse(fecha)).monto(new BigDecimal(monto)).tipo(tipo)
                .numeroComprobante(comprobante).descripcion(descripcion)
                .estado(EstadoMovimiento.PENDIENTE)
                .build();
    }
}
