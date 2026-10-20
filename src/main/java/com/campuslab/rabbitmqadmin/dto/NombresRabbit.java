package com.campuslab.rabbitmqadmin.dto;

/**
 * Reglas de nombre compartidas por los DTOs de entrada. RabbitMQ reserva el
 * prefijo "amq." para exchanges/colas del propio broker, asi que se rechaza
 * desde la validacion en vez de esperar el 403 de la Management API.
 */
public final class NombresRabbit {

    public static final String PATRON = "^(?!amq\\.)[A-Za-z0-9._:\\-]+$";
    public static final String MENSAJE = "solo puede contener letras, numeros, '.', '_', ':' o '-' "
            + "y no puede comenzar con 'amq.' (reservado por RabbitMQ)";
    public static final int LARGO_MAXIMO = 255;

    private NombresRabbit() {
    }
}
