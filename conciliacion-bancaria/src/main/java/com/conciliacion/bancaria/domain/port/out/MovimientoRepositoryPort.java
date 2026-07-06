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

    /** Todos los contables incluyendo CONCILIADO — usado para detectar duplicados en re-carga. */
    List<Movimiento> buscarTodosContablesPorConciliacion(Long idConciliacion);

    /** Suma de todos los movimientos AGRUPADO reales (excluye el sintético) para un tipo dado. */
    java.math.BigDecimal sumAgrupadosPorTipo(Long idConciliacion, String tipo);

    /**
     * Actualiza el monto del sintético "GASTOS BANCARIOS AGRUPADOS" PENDIENTE.
     * Retorna el número de filas afectadas (0 si el sintético no existe aún).
     */
    int actualizarMontoSintetico(Long idConciliacion, String tipo, java.math.BigDecimal monto);

    /** Elimina contables por sus IDs (para limpiar duplicados). */
    void eliminarContablesPorIds(List<Long> ids);

    /** Marca un movimiento bancario con el estado dado (ej. AGRUPADO, CONCILIADO). */
    void actualizarEstadoBancario(Long idMovimiento, com.conciliacion.bancaria.shared.EstadoMovimiento estado);

    /** Marca un movimiento contable con el estado dado (ej. CONCILIADO). */
    void actualizarEstadoContable(Long idMovimiento, com.conciliacion.bancaria.shared.EstadoMovimiento estado);
}