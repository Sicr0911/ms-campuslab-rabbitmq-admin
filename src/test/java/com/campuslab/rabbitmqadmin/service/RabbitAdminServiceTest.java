package com.campuslab.rabbitmqadmin.service;

import com.campuslab.rabbitmqadmin.client.RabbitMqManagementClient;
import com.campuslab.rabbitmqadmin.dto.BindingInfoDTO;
import com.campuslab.rabbitmqadmin.dto.QueueInfoDTO;
import com.campuslab.rabbitmqadmin.dto.QueueRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private RabbitMqManagementClient client;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        client = mock(RabbitMqManagementClient.class);
        service = new RabbitAdminService(client, List.of("q.cmd.email", "cmd.direct"));
    }

    @Test
    void crearCola_declaraConArgumentosDeDlq() {
        QueueRequestDTO request = new QueueRequestDTO("q.nueva", true, null, "cmd.dlx", "q.nueva.dlq", 60000, null);
        when(client.queue("q.nueva"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(cola("q.nueva", 0, Map.of())));

        service.crearCola(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> definicion = ArgumentCaptor.forClass(Map.class);
        verify(client).declareQueue(eq("q.nueva"), definicion.capture());
        assertThat(definicion.getValue()).containsEntry("durable", true).containsEntry("auto_delete", false);
        assertThat((Map<String, Object>) definicion.getValue().get("arguments"))
                .containsEntry("x-dead-letter-exchange", "cmd.dlx")
                .containsEntry("x-dead-letter-routing-key", "q.nueva.dlq")
                .containsEntry("x-message-ttl", 60000);
    }

    @Test
    void crearCola_queYaExiste_esConflicto() {
        when(client.queue("q.existe")).thenReturn(Optional.of(cola("q.existe", 0, Map.of())));

        assertThatThrownBy(() -> service.crearCola(new QueueRequestDTO("q.existe", true, null, null, null, null, null)))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(client, never()).declareQueue(anyString(), any());
    }

    @Test
    void eliminarColaProtegida_esRechazado() {
        assertThatThrownBy(() -> service.eliminarCola("q.cmd.email", false))
                .isInstanceOf(OperacionNoPermitidaException.class);
        verify(client, never()).deleteQueue(anyString(), anyBoolean());
    }

    @Test
    void eliminarColaInexistente_esNoEncontrado() {
        when(client.queue("q.fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarCola("q.fantasma", false))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarCola_conMensajes_noSeRecreaParaNoPerderlos() {
        when(client.queue("q.ocupada")).thenReturn(Optional.of(cola("q.ocupada", 12, Map.of())));

        assertThatThrownBy(() -> service.actualizarCola("q.ocupada",
                new QueueRequestDTO("q.ocupada", true, null, null, null, 5000, null)))
                .isInstanceOf(OperacionNoPermitidaException.class)
                .hasMessageContaining("12 mensajes");
        verify(client, never()).deleteQueue(anyString(), anyBoolean());
    }

    @Test
    void actualizarCola_vacia_seRecreaYRestauraSusBindings() {
        when(client.queue("q.vacia")).thenReturn(Optional.of(cola("q.vacia", 0, Map.of())));
        when(client.queueBindings("q.vacia")).thenReturn(List.of(
                new BindingInfoDTO("", "q.vacia", "queue", "q.vacia", Map.of(), "q.vacia"),
                new BindingInfoDTO("x.app", "q.vacia", "queue", "app.evento", Map.of(), "app.evento")));

        service.actualizarCola("q.vacia", new QueueRequestDTO("q.vacia", true, null, null, null, 5000, null));

        var orden = inOrder(client);
        orden.verify(client).deleteQueue("q.vacia", true);
        orden.verify(client).declareQueue(eq("q.vacia"), any());
        orden.verify(client).createBinding("x.app", "q", "q.vacia", "app.evento", Map.of());
        // el binding implicito al default exchange no se recrea
        verify(client, never()).createBinding(eq(""), anyString(), anyString(), anyString(), any());
    }

    @Test
    void actualizarCola_sinCambios_noTocaElBroker() {
        when(client.queue("q.igual")).thenReturn(Optional.of(cola("q.igual", 3, Map.of("x-message-ttl", 5000L))));

        service.actualizarCola("q.igual", new QueueRequestDTO("q.igual", true, false, null, null, 5000, null));

        verify(client, never()).deleteQueue(anyString(), anyBoolean());
        verify(client, never()).declareQueue(anyString(), any());
    }

    @Test
    void actualizarCola_conNombreDistintoEnElCuerpo_esSolicitudInvalida() {
        assertThatThrownBy(() -> service.actualizarCola("q.a",
                new QueueRequestDTO("q.b", true, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eliminarBindingInexistente_esNoEncontrado() {
        when(client.bindingsBetween("x.app", "q.app")).thenReturn(List.of());

        assertThatThrownBy(() -> service.eliminarBinding("x.app", "q.app", "rk"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarBinding_usaElPropertiesKeyDeRabbit() {
        when(client.bindingsBetween("x.app", "q.app")).thenReturn(List.of(
                new BindingInfoDTO("x.app", "q.app", "queue", "rk", Map.of(), "rk-props")));

        service.eliminarBinding("x.app", "q.app", "rk");

        verify(client).deleteBinding("x.app", "q.app", "rk-props");
    }

    private QueueInfoDTO cola(String name, int mensajes, Map<String, Object> argumentos) {
        return new QueueInfoDTO(name, "running", mensajes, mensajes, 0, 0, true, false, argumentos);
    }
}
