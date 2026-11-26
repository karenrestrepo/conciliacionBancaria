package com.conciliacion.bancaria.application.usecase.event;

/**
 * Solicitud de correr el motor de conciliación completo (todos los bancarios y contables de
 * la conciliación) para el job dado. Se publica desde dentro de un método
 * {@code @Transactional} (carga de extracto, reproceso) y se escucha con
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} -- así el motor nunca arranca
 * antes de que esa transacción externa haya hecho commit, sin importar desde qué método se
 * publique. Ver {@code CargaCsvUseCaseImpl.onMotorCompletoRequested}.
 */
public record MotorCompletoRequestedEvent(Long idConciliacion, String jobId) {
}
