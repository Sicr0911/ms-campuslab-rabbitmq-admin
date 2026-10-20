package com.campuslab.rabbitmqadmin.controller;

import com.campuslab.rabbitmqadmin.client.RabbitMqManagementException;
import com.campuslab.rabbitmqadmin.client.RabbitMqRechazoException;
import com.campuslab.rabbitmqadmin.service.OperacionNoPermitidaException;
import com.campuslab.rabbitmqadmin.service.RecursoNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RabbitMqManagementException.class)
    public ResponseEntity<Map<String, String>> handleRabbitMqUnavailable(RabbitMqManagementException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler({OperacionNoPermitidaException.class, RabbitMqRechazoException.class})
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    /** Errores de @Valid sobre el cuerpo: devuelve campo -> mensaje. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("message", "Solicitud invalida", "errores", errores));
    }

    /** Errores de validacion sobre @RequestParam/@PathVariable (ej. @NotBlank). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidParams(HandlerMethodValidationException ex) {
        List<String> errores = ex.getAllErrors().stream()
                .map(e -> e.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(Map.of("message", "Solicitud invalida", "errores", errores));
    }
}
