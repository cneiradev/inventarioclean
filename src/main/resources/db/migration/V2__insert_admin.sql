-- =====================================================================
-- V2: Insertar usuario administrador inicial
-- =====================================================================
-- Este migration inserta un usuario admin para desarrollo local.
-- La password_hash es un hash BCrypt generado para "admin123".
-- En produccion este usuario deberia crearse via el flujo de la app o
-- el admin deberia ser creado por otro admin existente.
-- =====================================================================
-- NOTAS:
--   - El password_hash ya viene en formato BCrypt listo para comparar
--   - El username y email deben ser unicos (validados por CONSTRAINT)
--   - El rol es 'ADMINISTRADOR' segun el CHECK constraint de la tabla
-- =====================================================================
INSERT INTO usuario (nombre, apellido, email, username, password_hash, rol)
VALUES (
    'Administrador',
    'Sistema',
    'admin@inventario.local',
    'ADMIN',
    '$2b$12$2hJTsn2LZvQU9YVmeht05Or7CZJWwu/z.jeSu6vnJOh./WXgJMkgm',
    'ADMINISTRADOR'
);