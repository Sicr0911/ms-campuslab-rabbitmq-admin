package com.campuslab.rabbitmqadmin;

import com.campuslab.rabbitmqadmin.controller.RabbitMqAdminController;
import com.campuslab.rabbitmqadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de carga de contexto. No necesita RabbitMQ ni el IdP: la Management
 * API solo se llama al invocar un endpoint, y el JwtDecoder de Spring Boot
 * resuelve el issuer-uri de forma perezosa (al validar el primer token).
 */
@SpringBootTest
class MsCampuslabRabbitmqAdminApplicationTests {

    @Autowired
    private RabbitMqAdminController controller;

    @Autowired
    private RabbitAdminService service;

    @Test
    void contextLoads() {
        assertThat(controller).isNotNull();
        assertThat(service).isNotNull();
    }
}
