package com.campuslab.rabbitmqadmin.dto;

/** Resultado de devolver los mensajes de una DLQ a su cola de origen. */
public record ReprocesoDlqDTO(String dlq, int reprocesados, int devueltosALaDlq) {
}
