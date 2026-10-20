package com.campuslab.rabbitmqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /queues (crear) y PUT /queues/{name} (actualizar).
 *
 * @param durable              obligatorio: no se puede cambiar sin recrear la cola,
 *                             asi que se exige que el cliente lo decida explicitamente
 * @param autoDelete           opcional, por defecto false
 * @param deadLetterExchange   opcional: x-dead-letter-exchange
 * @param deadLetterRoutingKey opcional: x-dead-letter-routing-key
 * @param messageTtl           opcional: x-message-ttl en milisegundos
 * @param maxLength            opcional: x-max-length (cantidad de mensajes)
 */
public record QueueRequestDTO(
        @NotBlank(message = "el nombre de la cola es obligatorio")
        @Size(max = NombresRabbit.LARGO_MAXIMO)
        @Pattern(regexp = NombresRabbit.PATRON, message = NombresRabbit.MENSAJE)
        String name,

        @NotNull(message = "durable es obligatorio")
        Boolean durable,

        Boolean autoDelete,

        @Size(max = NombresRabbit.LARGO_MAXIMO)
        @Pattern(regexp = NombresRabbit.PATRON, message = NombresRabbit.MENSAJE)
        String deadLetterExchange,

        @Size(max = NombresRabbit.LARGO_MAXIMO)
        String deadLetterRoutingKey,

        @Positive(message = "messageTtl debe ser mayor que 0")
        Integer messageTtl,

        @Positive(message = "maxLength debe ser mayor que 0")
        Integer maxLength
) {
}
