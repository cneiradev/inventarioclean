package com.inventarioclean.auth.dto;

import com.inventarioclean.usuario.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Respuesta del login: el token y los datos minimos del usuario.
 *
 * <p>El frontend usa el rol para armar el menu lateral y las rutas, pero la
 * autorizacion real la aplica el backend.
 */
@Getter
@Builder
@AllArgsConstructor
public class LoginResponse {

    private final String token;
    private final String tipoToken;
    private final Long usuarioId;
    private final String username;
    private final String nombre;
    private final String apellido;
    private final String nombreCompleto;
    private final String iniciales;
    private final Rol rol;
}

