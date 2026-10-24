package com.inventarioclean.common.exception;

/**
 * La operacion es valida en forma pero no se puede ejecutar: no hay stock
 * suficiente, el username ya existe, la renta ya fue retornada.
 *
 * <p>Se responde 422 (Unprocessable Entity) y no 400, porque el JSON era
 * correcto: el problema esta en el estado actual de los datos.
 */
public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}

