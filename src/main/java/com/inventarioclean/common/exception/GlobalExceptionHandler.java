package com.inventarioclean.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo uniforme de errores de la API.
 *
 * <p>Regla del proyecto: el mensaje de error debe decir exactamente que paso
 * ("el cliente pedio controles que nos dira que paso exactamente"). Por eso
 * cada excepcion responde con un mensaje legible en espanol y nunca con un
 * stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Error de negocio previsto (stock insuficiente, SKU inexistente...). */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> notFound(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(cuerpo(e.getMessage()));
    }

    /** Operacion invalida: stock insuficiente, usuario ya existe, etc. */
    @ExceptionHandler(OperacionInvalidaException.class)
    public ResponseEntity<Map<String, Object>> invalid(OperacionInvalidaException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(cuerpo(e.getMessage()));
    }

    /** Credenciales incorrectas: 401 generico, sin revelar si el user existe. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, Object>> credenciales(CredencialesInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(cuerpo(e.getMessage()));
    }

    /** Fallo de validacion de DTO: se listan todos los campos con problema. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> campos.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        Map<String, Object> body = cuerpo("Revisa los datos ingresados");
        body.put("campos", campos);
        return ResponseEntity.badRequest().body(body);
    }

    private Map<String, Object> cuerpo(String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("error", mensaje);
        return body;
    }
}

