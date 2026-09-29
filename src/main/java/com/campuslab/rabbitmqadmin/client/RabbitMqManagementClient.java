package com.campuslab.rabbitmqadmin.client;

import com.campuslab.rabbitmqadmin.dto.OverviewDTO;
import com.campuslab.rabbitmqadmin.dto.QueueInfoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Cliente HTTP hacia la Management API de RabbitMQ (puerto 15672, la misma
 * UI que "management" expone como REST). Este microservicio no se conecta
 * al cluster por AMQP: solo consulta su estado via HTTP, en solo lectura.
 */
@Component
public class RabbitMqManagementClient {

    private final RestClient restClient;

    public RabbitMqManagementClient(
            @Value("${campuslab.rabbitmq.management-url}") String managementUrl,
            @Value("${campuslab.rabbitmq.management-username}") String username,
            @Value("${campuslab.rabbitmq.management-password}") String password) {

        String credenciales = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(managementUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credenciales)
                .build();
    }

    public OverviewDTO overview() {
        try {
            return restClient.get()
                    .uri("/api/overview")
                    .retrieve()
                    .body(OverviewDTO.class);
        } catch (RestClientException ex) {
            throw new RabbitMqManagementException("No fue posible consultar el overview de RabbitMQ", ex);
        }
    }

    public List<QueueInfoDTO> queues() {
        try {
            return restClient.get()
                    .uri("/api/queues")
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<QueueInfoDTO>>() {
                    });
        } catch (RestClientException ex) {
            throw new RabbitMqManagementException("No fue posible consultar las colas de RabbitMQ", ex);
        }
    }
}
