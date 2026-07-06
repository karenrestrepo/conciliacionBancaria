-- V17: Permisos granulares por usuario
-- Almacena el conjunto efectivo de permisos de cada usuario.
-- El admin carga los permisos por defecto del rol y puede habilitarlos o quitarlos.

CREATE TABLE usuario_permisos (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT      NOT NULL,
    permiso    VARCHAR(50) NOT NULL,
    CONSTRAINT fk_up_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT uk_usuario_permiso UNIQUE (usuario_id, permiso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_up_usuario ON usuario_permisos(usuario_id);
