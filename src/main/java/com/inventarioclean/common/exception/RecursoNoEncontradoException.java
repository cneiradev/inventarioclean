package com.inventarioclean.common.exception;

/** El recurso solicitado no existe (SKU, username, codigo de renta...). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

