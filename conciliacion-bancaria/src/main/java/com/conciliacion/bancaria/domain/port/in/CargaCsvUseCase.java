package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.Movimiento;
import com.conciliacion.bancaria.domain.model.ResumenCargaAuxiliar;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CargaCsvUseCase {

    // Retorna el job_id para seguimiento por polling (TRD RT-03)
    String cargarExtractoBancario(Long idConciliacion, MultipartFile archivo,
                                  String nombreBanco);

    /** Guarda movimientos del extracto ya verificados y aprobados; retorna el job_id del motor. */
    String confirmarExtractoLeido(Long idConciliacion, List<Movimiento> bancarios);

    ResumenCargaAuxiliar cargarLibroAuxiliar(Long idConciliacion, MultipartFile archivo);

    /** Recarga el auxiliar con movimientos ya leídos y verificados. */
    ResumenCargaAuxiliar cargarAuxiliarLeido(Long idConciliacion, List<Movimiento> contables);

    String reprocesarMotor(Long idConciliacion);
}