package com.conciliacion.bancaria.application.usecase;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;
import com.conciliacion.bancaria.domain.exception.RecursoNoEncontradoException;
import com.conciliacion.bancaria.domain.model.Conciliacion;
import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.RevisionLectura;
import com.conciliacion.bancaria.domain.port.in.CargaCsvUseCase;
import com.conciliacion.bancaria.domain.port.in.LecturaIaUseCase;
import com.conciliacion.bancaria.domain.port.out.ConciliacionRepositoryPort;
import com.conciliacion.bancaria.domain.port.out.LectorExtractoIaPort;
import com.conciliacion.bancaria.domain.service.RevisorLecturaExtracto;
import com.conciliacion.bancaria.shared.EstadoMovimiento;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LecturaIaUseCaseImpl implements LecturaIaUseCase {

    private final LectorExtractoIaPort lector;
    private final RevisorLecturaExtracto revisor;
    private final ConciliacionRepositoryPort conciliacionRepo;
    private final CargaCsvUseCase cargaCsvUseCase;

    @Override
    public RevisionLectura previsualizar(Long idConciliacion, byte[] pdf) {
        Conciliacion conciliacion = buscar(idConciliacion);
        if (pdf == null || pdf.length < 5
                || !"%PDF-".equals(new String(pdf, 0, 5, StandardCharsets.US_ASCII))) {
            throw new CsvValidationException("El archivo no es un PDF");
        }
        RevisionLectura revision = revisor.revisar(
                lector.leer(pdf, conciliacion.getPeriodo()), conciliacion.getPeriodo());
        if (!lector.esSimulado()) {
            return revision;
        }
        List<String> advertencias = new ArrayList<>();
        advertencias.add("DATOS SIMULADOS: no corresponden al archivo subido. No los apruebe en una conciliación real");
        advertencias.addAll(revision.getAdvertencias());
        return revision.toBuilder().advertencias(advertencias).build();
    }

    @Override
    public String confirmar(Long idConciliacion, LecturaExtracto lectura, boolean aceptaSinCuadre) {
        Conciliacion conciliacion = buscar(idConciliacion);
        RevisionLectura revision = revisor.revisar(lectura, conciliacion.getPeriodo());

        if (!revision.getErrores().isEmpty()) {
            throw new CsvValidationException("No se puede aprobar: " + String.join("; ", revision.getErrores()));
        }
        if (revision.getEstadoCuadre() == RevisionLectura.EstadoCuadre.NO_CUADRA) {
            throw new CsvValidationException("No se puede aprobar: el extracto no cuadra");
        }
        if (revision.getEstadoCuadre() == RevisionLectura.EstadoCuadre.NO_VERIFICABLE && !aceptaSinCuadre) {
            throw new CsvValidationException(
                    "El cuadre no se puede verificar. Revise los movimientos y confirme que desea cargarlos");
        }

        String tarjeta = "TARJETA_CREDITO".equals(lectura.getTipoProducto())
                && lectura.getUltimosDigitosTarjeta() != null
                && lectura.getUltimosDigitosTarjeta().matches("\\d{4}")
                ? lectura.getUltimosDigitosTarjeta() : null;

        List<Movimiento> movimientos = lectura.getMovimientos().stream()
                .map(m -> Movimiento.builder()
                        .fecha(LocalDate.parse(m.getFecha()))
                        .descripcion(m.getDescripcion().trim())
                        .monto(m.getMonto())
                        .tipo(m.getTipo())
                        .estado(EstadoMovimiento.PENDIENTE)
                        .ultimosDigitosTarjeta(tarjeta)
                        .build())
                .toList();

        return cargaCsvUseCase.confirmarExtractoLeido(idConciliacion, movimientos);
    }

    private Conciliacion buscar(Long idConciliacion) {
        return conciliacionRepo.buscarPorId(idConciliacion)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la conciliación " + idConciliacion));
    }
}
