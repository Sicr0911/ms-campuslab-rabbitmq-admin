package com.campuslab.rabbitmqadmin.client;

/**
 * La Management API respondio 4xx: RabbitMQ estaba disponible pero rechazo
 * la operacion (a diferencia de RabbitMqManagementException, que indica que
 * no se pudo hablar con el broker).
 */
public class RabbitMqRechazoException extends RuntimeException {

    public RabbitMqRechazoException(String message, Throwable cause) {
        super(message, cause);
    }
}
