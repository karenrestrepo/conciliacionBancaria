-- V11: Configuración de extractos bancarios
-- Permite definir cómo parsear archivos de extracto para cada banco

CREATE TABLE configuraciones_extracto (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_banco BIGINT NOT NULL,
    nombre VARCHAR(200) NOT NULL,
    tipo_archivo VARCHAR(10) NOT NULL,
    aplica_para_todas_las_cuentas BOOLEAN NOT NULL DEFAULT TRUE,
    configuracion_detalle TEXT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_modificacion DATETIME NULL,
    CONSTRAINT fk_config_extracto_banco FOREIGN KEY (id_banco) REFERENCES bancos(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE configuraciones_extracto_cuentas (
    id_configuracion BIGINT NOT NULL,
    id_cuenta BIGINT NOT NULL,
    PRIMARY KEY (id_configuracion, id_cuenta),
    CONSTRAINT fk_cec_config FOREIGN KEY (id_configuracion) REFERENCES configuraciones_extracto(id) ON DELETE CASCADE,
    CONSTRAINT fk_cec_cuenta FOREIGN KEY (id_cuenta) REFERENCES cuentas(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
