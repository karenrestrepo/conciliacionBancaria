package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.Movimiento;
import java.util.List;

public interface MovimientoRepositoryPort {

    List<Movimiento> guardarBancarios(List<Movimiento> movimientos, Long idConciliacion);

    List<Movimiento> guardarContables(List<Movimiento> movimientos, Long idConciliacion);

    List<Movimiento> buscarBancariosPorConciliacion(Long idConciliacion);

    List<Movimiento> buscarContablesPorConciliacion(Long idConciliacion);

    Movimiento actualizar(Movimiento movimiento, Long idConciliacion, String tipo);

    void resetEstadosBancarios(Long idConciliacion);

    void resetEstadosContables(Long idConciliacion);
}