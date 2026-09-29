package com.campuslab.rabbitmqadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subconjunto curado de lo que devuelve GET /api/overview de la Management
 * API de RabbitMQ.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OverviewDTO(
        String rabbitmq_version,
        String cluster_name,
        QueueTotals queue_totals,
        ObjectTotals object_totals
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueueTotals(Integer messages, Integer messages_ready, Integer messages_unacknowledged) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectTotals(Integer queues, Integer exchanges, Integer connections, Integer consumers) {
    }
}
