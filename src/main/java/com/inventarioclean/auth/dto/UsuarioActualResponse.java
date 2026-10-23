package com.inventarioclean.auth.dto;

import com.inventarioclean.usuario.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Datos del usuario autenticado, devueltos por {@code GET /api/auth/me}.
 *
 * <p>Es un DTO aparte de {@link LoginResponse} a proposito: el login incluye
 * el token, pero al rehidratar la sesion tras recargar la pagina no hay token
 * que devolver (viene en la cabecera), y mezclar ambos deja campos en null.
 */
@Getter
@Builder
@AllArgsConstructor
public class UsuarioActualResponse {

    private final Long usuarioId;
    private final String username;
    private final String nombre;
    private final String apellido;
    private final String nombreCompleto;
    private final String iniciales;
    private final Rol rol;
}

