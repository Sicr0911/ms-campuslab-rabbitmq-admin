package com.campuslab.rabbitmqadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Subconjunto de lo que devuelve GET /api/bindings de la Management API.
 * properties_key es el identificador que exige RabbitMQ para borrar un
 * binding concreto (DELETE /api/bindings/.../{props}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BindingInfoDTO(
        String source,
        String destination,
        String destination_type,
        String routing_key,
        Map<String, Object> arguments,
        String properties_key
) {
}
