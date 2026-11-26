package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.LecturaExtracto;
import com.conciliacion.bancaria.domain.model.ResultadoLectura;

public interface LecturaIaUseCase {

    /** Lee y verifica el PDF. Si la verificación es completa, lo guarda y lanza el motor. */
    ResultadoLectura procesar(Long idConciliacion, byte[] pdf);

    /**
     * Guarda lo que el usuario aprobó (se vuelve a verificar) y retorna el job_id del motor.
     *
     * @param aceptaSinCuadre el usuario declara que revisó un extracto cuyo cuadre no se puede verificar
     */
    String confirmar(Long idConciliacion, LecturaExtracto lectura, boolean aceptaSinCuadre);
}
