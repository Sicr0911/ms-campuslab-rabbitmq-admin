package com.campuslab.rabbitmqadmin.controller;

import com.campuslab.rabbitmqadmin.client.RabbitMqManagementClient;
import com.campuslab.rabbitmqadmin.dto.OverviewDTO;
import com.campuslab.rabbitmqadmin.dto.QueueInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de solo lectura sobre el estado del cluster RabbitMQ de CampusLab
 * (ec2-mq), pensada para un panel operativo de Admin. No permite crear,
 * purgar ni borrar colas: es un microservicio de observabilidad, no de
 * administracion destructiva del broker.
 */
@RestController
@RequestMapping("/api/admin/rabbitmq")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RabbitMqAdminController {

    private final RabbitMqManagementClient client;

    /** GET /api/admin/rabbitmq/overview */
    @GetMapping("/overview")
    public ResponseEntity<OverviewDTO> overview() {
        return ResponseEntity.ok(client.overview());
    }

    /** GET /api/admin/rabbitmq/queues */
    @GetMapping("/queues")
    public ResponseEntity<List<QueueInfoDTO>> queues() {
        return ResponseEntity.ok(client.queues());
    }
}
