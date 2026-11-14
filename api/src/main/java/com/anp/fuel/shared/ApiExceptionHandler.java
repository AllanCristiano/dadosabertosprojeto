package com.anp.fuel.shared;

import com.anp.fuel.alerta.AlertaException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalid(ConstraintViolationException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(AlertaException.NaoEncontrada.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> naoEncontrada(AlertaException e) { return Map.of("error", e.getMessage()); }

    @ExceptionHandler(AlertaException.Duplicada.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> duplicada(AlertaException e) { return Map.of("error", e.getMessage()); }

    @ExceptionHandler(AlertaException.Invalida.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, String> invalida(AlertaException e) { return Map.of("error", e.getMessage()); }
}
