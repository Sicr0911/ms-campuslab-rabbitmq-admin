package com.campuslab.rabbitmqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /exchanges (crear) y PUT /exchanges/{name} (actualizar).
 */
public record ExchangeRequestDTO(
        @NotBlank(message = "el nombre del exchange es obligatorio")
        @Size(max = NombresRabbit.LARGO_MAXIMO)
        @Pattern(regexp = NombresRabbit.PATRON, message = NombresRabbit.MENSAJE)
        String name,

        @NotBlank(message = "el tipo de exchange es obligatorio")
        @Pattern(regexp = "direct|fanout|topic|headers",
                message = "el tipo debe ser direct, fanout, topic o headers")
        String type,

        @NotNull(message = "durable es obligatorio")
        Boolean durable,

        Boolean autoDelete,

        Boolean internal
) {
}
