-- V15: Tabla de empresas (tenants del sistema)
-- Cada empresa se identifica por Cédula o NIT único

CREATE TABLE empresas (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_identificacion   ENUM('CEDULA','NIT') NOT NULL,
    numero_identificacion VARCHAR(20)          NOT NULL,
    digito_verificacion   VARCHAR(1)           NULL,      -- solo para NIT
    nombre                VARCHAR(200)         NOT NULL,
    activo                BOOLEAN              NOT NULL DEFAULT TRUE,
    ts_creacion           DATETIME             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_empresa_identificacion UNIQUE (tipo_identificacion, numero_identificacion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
