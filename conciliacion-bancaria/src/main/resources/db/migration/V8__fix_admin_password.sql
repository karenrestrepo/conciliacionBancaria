-- V8: Corrección del hash BCrypt del usuario admin
-- Password: Admin1234! (BCrypt cost=12)
UPDATE usuarios
SET password_hash = '$2a$12$tur7UlJqObqoH0pUSgFqV.Q3oP0.cCpyo3r5r1SQFn9C0GaXZgdim'
WHERE email = 'admin@conciliacion.com';