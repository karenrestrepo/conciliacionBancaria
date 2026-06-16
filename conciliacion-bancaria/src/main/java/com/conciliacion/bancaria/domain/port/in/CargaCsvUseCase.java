package com.conciliacion.bancaria.domain.port.in;

import org.springframework.web.multipart.MultipartFile;

public interface CargaCsvUseCase {

    // Retorna el job_id para seguimiento por polling (TRD RT-03)
    String cargarExtractoBancario(Long idConciliacion, MultipartFile archivo,
                                  String nombreBanco);

    String cargarLibroAuxiliar(Long idConciliacion, MultipartFile archivo);

    String reprocesarMotor(Long idConciliacion);
}