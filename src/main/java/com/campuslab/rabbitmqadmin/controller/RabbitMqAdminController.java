package com.campuslab.rabbitmqadmin.controller;

import com.campuslab.rabbitmqadmin.dto.BindingInfoDTO;
import com.campuslab.rabbitmqadmin.dto.BindingRequestDTO;
import com.campuslab.rabbitmqadmin.dto.ExchangeInfoDTO;
import com.campuslab.rabbitmqadmin.dto.ExchangeRequestDTO;
import com.campuslab.rabbitmqadmin.dto.OverviewDTO;
import com.campuslab.rabbitmqadmin.dto.QueueInfoDTO;
import com.campuslab.rabbitmqadmin.dto.QueueRequestDTO;
import com.campuslab.rabbitmqadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de administracion del cluster RabbitMQ de CampusLab (ec2-mq), pensada
 * para el panel operativo de Admin: consulta el estado del broker y permite
 * crear, actualizar y eliminar colas, exchanges y bindings. La logica de
 * negocio vive en RabbitAdminService; este controller solo valida la
 * entrada (@Valid) y traduce a HTTP.
 */
@RestController
@RequestMapping("/api/admin/rabbitmq")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RabbitMqAdminController {

    private final RabbitAdminService service;

    /** GET /api/admin/rabbitmq/overview */
    @GetMapping("/overview")
    public ResponseEntity<OverviewDTO> overview() {
        return ResponseEntity.ok(service.overview());
    }

    // ------------------------------------------------------------------ colas

    /** GET /api/admin/rabbitmq/queues */
    @GetMapping("/queues")
    public ResponseEntity<List<QueueInfoDTO>> queues() {
        return ResponseEntity.ok(service.listarColas());
    }

    /** POST /api/admin/rabbitmq/queues */
    @PostMapping("/queues")
    public ResponseEntity<QueueInfoDTO> crearCola(@Valid @RequestBody QueueRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCola(request));
    }

    /** PUT /api/admin/rabbitmq/queues/{name} */
    @PutMapping("/queues/{name}")
    public ResponseEntity<QueueInfoDTO> actualizarCola(@PathVariable String name,
                                                       @Valid @RequestBody QueueRequestDTO request) {
        return ResponseEntity.ok(service.actualizarCola(name, request));
    }

    /** DELETE /api/admin/rabbitmq/queues/{name}?ifEmpty=true */
    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String name,
                                             @RequestParam(defaultValue = "false") boolean ifEmpty) {
        service.eliminarCola(name, ifEmpty);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------- exchanges

    /** GET /api/admin/rabbitmq/exchanges */
    @GetMapping("/exchanges")
    public ResponseEntity<List<ExchangeInfoDTO>> exchanges() {
        return ResponseEntity.ok(service.listarExchanges());
    }

    /** POST /api/admin/rabbitmq/exchanges */
    @PostMapping("/exchanges")
    public ResponseEntity<ExchangeInfoDTO> crearExchange(@Valid @RequestBody ExchangeRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearExchange(request));
    }

    /** PUT /api/admin/rabbitmq/exchanges/{name} */
    @PutMapping("/exchanges/{name}")
    public ResponseEntity<ExchangeInfoDTO> actualizarExchange(@PathVariable String name,
                                                              @Valid @RequestBody ExchangeRequestDTO request) {
        return ResponseEntity.ok(service.actualizarExchange(name, request));
    }

    /** DELETE /api/admin/rabbitmq/exchanges/{name}?ifUnused=true */
    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String name,
                                                 @RequestParam(defaultValue = "false") boolean ifUnused) {
        service.eliminarExchange(name, ifUnused);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------------- bindings

    /** GET /api/admin/rabbitmq/bindings */
    @GetMapping("/bindings")
    public ResponseEntity<List<BindingInfoDTO>> bindings() {
        return ResponseEntity.ok(service.listarBindings());
    }

    /** POST /api/admin/rabbitmq/bindings */
    @PostMapping("/bindings")
    public ResponseEntity<BindingInfoDTO> crearBinding(@Valid @RequestBody BindingRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearBinding(request));
    }

    /** DELETE /api/admin/rabbitmq/bindings?exchange=...&queue=...&routingKey=... */
    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@RequestParam @NotBlank String exchange,
                                                @RequestParam @NotBlank String queue,
                                                @RequestParam(defaultValue = "") String routingKey) {
        service.eliminarBinding(exchange, queue, routingKey);
        return ResponseEntity.noContent().build();
    }
}
