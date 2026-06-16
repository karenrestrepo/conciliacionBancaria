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

    void resetEstadosBancariosSugeridos(Long idConciliacion);

    void resetEstadosContablesSugeridos(Long idConciliacion);

    List<Movimiento> buscarBancariosPendientesPorConciliacion(Long idConciliacion);

    List<Movimiento> buscarBancariosPorIds(List<Long> ids);

    /** Devuelve los movimientos del extracto que fueron agrupados como gastos bancarios (estado AGRUPADO). */
    List<Movimiento> buscarBancariosAgrupadosPorConciliacion(Long idConciliacion);

    /** Elimina todos los movimientos bancarios que no están CONCILIADO (para re-carga del extracto). */
    void eliminarBancariosNoConciliadosPorConciliacion(Long idConciliacion);

    /** Elimina todos los movimientos contables que no están CONCILIADO (para re-carga del auxiliar). */
    void eliminarContablesNoConciliadosPorConciliacion(Long idConciliacion);

    /** Marca un movimiento bancario con el estado dado (ej. AGRUPADO). */
    void actualizarEstadoBancario(Long idMovimiento, com.conciliacion.bancaria.shared.EstadoMovimiento estado);
}