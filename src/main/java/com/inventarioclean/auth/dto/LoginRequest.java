package com.inventarioclean.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body del endpoint de login.
 *
 * <p>{@code username} es el identificador tipo FPEREZ, no el email. El
 * backend normaliza a mayusculas antes de buscar, para que escribir
 * "fprez" tambien funcione.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El usuario es obligatorio")
    private String username;

    @NotBlank(message = "La contrasena es obligatoria")
    private String password;
}

