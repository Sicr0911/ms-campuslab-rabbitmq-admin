package com.campuslab.rabbitmqadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subconjunto curado de lo que devuelve GET /api/queues de la Management
 * API de RabbitMQ (el resto de campos no nos interesa para este panel).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QueueInfoDTO(
        String name,
        String state,
        Integer messages,
        Integer messages_ready,
        Integer messages_unacknowledged,
        Integer consumers
) {
}
