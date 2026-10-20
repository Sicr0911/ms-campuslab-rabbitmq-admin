package com.campuslab.rabbitmqadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de POST /bindings: enlaza un exchange con una cola.
 *
 * @param routingKey obligatorio pero puede ser "" (ej. exchanges fanout)
 */
public record BindingRequestDTO(
        @NotBlank(message = "el exchange de origen es obligatorio")
        @Size(max = NombresRabbit.LARGO_MAXIMO)
        @Pattern(regexp = NombresRabbit.PATRON, message = NombresRabbit.MENSAJE)
        String exchange,

        @NotBlank(message = "la cola de destino es obligatoria")
        @Size(max = NombresRabbit.LARGO_MAXIMO)
        @Pattern(regexp = NombresRabbit.PATRON, message = NombresRabbit.MENSAJE)
        String queue,

        @NotNull(message = "routingKey es obligatorio (usa \"\" si el exchange lo ignora)")
        @Size(max = NombresRabbit.LARGO_MAXIMO)
        String routingKey
) {
}
