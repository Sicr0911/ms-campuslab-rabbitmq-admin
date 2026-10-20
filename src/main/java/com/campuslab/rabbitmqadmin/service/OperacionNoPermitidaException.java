package com.campuslab.rabbitmqadmin.service;

/**
 * La operacion es valida sintacticamente pero choca con el estado actual
 * del broker o con una regla de negocio (recurso protegido, cola con
 * mensajes, recurso ya existente, etc.). Se traduce a HTTP 409.
 */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String message) {
        super(message);
    }
}
