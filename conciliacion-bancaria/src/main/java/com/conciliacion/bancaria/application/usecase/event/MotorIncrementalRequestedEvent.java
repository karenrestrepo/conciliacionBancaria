package com.conciliacion.bancaria.application.usecase.event;

import com.conciliacion.bancaria.domain.model.Movimiento;

import java.util.List;

/**
 * Solicitud de correr el motor de conciliación incremental (nuevos contables contra
 * bancarios sin sugerencia activa) para el job dado. Misma razón de ser que
 * {@link MotorCompletoRequestedEvent} -- se publica desde dentro de la transacción de la
 * carga del auxiliar y solo se procesa después de que esa transacción hace commit.
 */
public record MotorIncrementalRequestedEvent(Long idConciliacion, String jobId, List<Movimiento> nuevosContables) {
}
