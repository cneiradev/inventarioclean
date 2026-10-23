package com.inventarioclean.common.exception;

/**
 * Credenciales incorrectas en el login.
 *
 * <p>El mensaje es deliberadamente generico: no revela si el usuario existe
 * ni cual parte fallo, para no entregar informacion a quien prueba passwords.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Usuario o contrasena incorrectos");
    }
}

