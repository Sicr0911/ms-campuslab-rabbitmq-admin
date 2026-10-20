package com.campuslab.rabbitmqadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Subconjunto de lo que devuelve GET /api/exchanges de la Management API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeInfoDTO(
        String name,
        String type,
        Boolean durable,
        Boolean auto_delete,
        Boolean internal,
        Map<String, Object> arguments
) {
}
