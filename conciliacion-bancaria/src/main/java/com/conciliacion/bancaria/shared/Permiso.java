package com.conciliacion.bancaria.shared;

import java.util.EnumSet;
import java.util.Set;

public enum Permiso {

    VER_CONCILIACIONES,
    CREAR_CONCILIACION,
    APROBAR_CONCILIACION,
    CERRAR_CONCILIACION,
    VER_MOVIMIENTOS,
    GESTIONAR_BANCOS,
    GESTIONAR_EXTRACTOS,
    CARGAR_ARCHIVOS,
    GESTIONAR_USUARIOS;   // exclusivo del ADMIN-root, no asignable a otros usuarios

    public static Set<Permiso> defaultsParaRol(Rol rol) {
        return switch (rol) {
            case AUXILIAR -> EnumSet.of(
                    VER_CONCILIACIONES, CREAR_CONCILIACION,
                    VER_MOVIMIENTOS, CARGAR_ARCHIVOS);
            case CONTADOR -> EnumSet.of(
                    VER_CONCILIACIONES, CREAR_CONCILIACION,
                    VER_MOVIMIENTOS, CARGAR_ARCHIVOS,
                    APROBAR_CONCILIACION, CERRAR_CONCILIACION,
                    GESTIONAR_BANCOS, GESTIONAR_EXTRACTOS);
            case FINANZAS -> EnumSet.of(
                    VER_CONCILIACIONES, VER_MOVIMIENTOS);
            case ADMIN -> EnumSet.allOf(Permiso.class);
        };
    }

    /** Permisos que se pueden asignar a usuarios no-ADMIN. */
    public static Set<Permiso> asignables() {
        return EnumSet.complementOf(EnumSet.of(GESTIONAR_USUARIOS));
    }
}
