package com.campuslab.rabbitmqadmin.controller;

import com.campuslab.rabbitmqadmin.client.RabbitMqManagementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RabbitMqManagementException.class)
    public ResponseEntity<Map<String, String>> handleRabbitMqUnavailable(RabbitMqManagementException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
    }
}
