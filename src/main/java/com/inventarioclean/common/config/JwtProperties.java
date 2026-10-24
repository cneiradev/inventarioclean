package com.inventarioclean.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

/**
 * Propiedades de la sesion JWT, leidas de app.jwt.*.
 *
 * <p>El {@code secret} no tiene valor por defecto a proposito: si falta, la
 * app falla el arranque con un error claro en vez de arrancar con una clave
 * debil. En desarrollo el perfil local define uno solo para local.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "Falta app.jwt.secret (variable de entorno JWT_SECRET)")
        String secret,

        @Min(value = 5, message = "La sesion no puede durar menos de 5 minutos")
        long expirationMinutes
) {
}

