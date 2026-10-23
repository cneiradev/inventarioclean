package com.inventarioclean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API.
 *
 * <p>Arquitectura: monolito modular. Los modulos de dominio (auth, usuario,
 * producto, movimiento, auditoria) viven en paquetes separados bajo
 * com.inventarioclean, pero en un solo proceso y un solo deploy.
 *
 * <p>{@link ConfigurationPropertiesScan} va en la clase principal a proposito:
 * puesto en un {@code @Configuration} de un subpaquete, solo escaneara ese
 * subpaquete y las clases anotadas en otros (por ejemplo app.jwt.*) no
 * tendrian bean, y la app fallaria al arrancar.
 *
 * <p>Se excluye {@link UserDetailsServiceAutoConfiguration} porque aqui la
 * autenticacion la hace JwtAuthenticationFilter contra la tabla usuario. Sin
 * esta exclusion Spring genera un usuario por defecto ("Using generated
 * security password") que no existe en la base y solo ensucia los logs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class InventarioCleanApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventarioCleanApplication.class, args);
    }
}
