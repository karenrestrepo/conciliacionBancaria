-- V1: Usuarios y roles (autenticación RBAC)
-- Roles: AUXILIAR, CONTADOR, FINANZAS, ADMIN

CREATE TABLE usuarios (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre        VARCHAR(100)        NOT NULL,
    email         VARCHAR(150)        NOT NULL UNIQUE,
    password_hash VARCHAR(255)        NOT NULL,
    rol           ENUM('AUXILIAR','CONTADOR','FINANZAS','ADMIN') NOT NULL,
    activo        BOOLEAN             NOT NULL DEFAULT TRUE,
    ts_creacion   DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Usuario admin por defecto (password: Admin1234! hasheado con BCrypt cost=12)
-- IMPORTANTE: cambiar en producción
INSERT INTO usuarios (nombre, email, password_hash, rol)
VALUES ('Administrador', 'admin@conciliacion.com',
        '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'ADMIN');
