package com.inventarioclean.usuario.dto;

import com.inventarioclean.usuario.Rol;

/**
 * Vista de un usuario para la tabla de gestion.
 *
 * <p>Nunca incluye el hash de la contrasena: la entidad no se serializa
 * directamente, se usa este DTO. Asi es imposible filtrar la clave por
 * accidente en una respuesta de la API.
 */
public record UsuarioResponse(
        Long id,
        String username,
        String nombre,
        String apellido,
        String nombreCompleto,
        String iniciales,
        String email,
        Rol rol,
        boolean activo
) {
}
