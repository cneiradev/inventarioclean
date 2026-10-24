package com.inventarioclean.usuario;

/**
 * Roles del sistema, en jerarquia de mayor a menor permisos.
 *
 * <p>ADMINISTRADOR - CRUD completo (usuarios y productos).
 * <p>OPERADOR     - consulta stock e inventario.
 *
 * <p>La autorizacion se aplica SIEMPRE en el servidor con
 * {@code @PreAuthorize}. Ocultar botones en el frontend es UX, nunca seguridad.
 */
public enum Rol {

    ADMINISTRADOR,
    OPERADOR
}

