package com.campuslab.rabbitmqadmin.controller;

import com.campuslab.rabbitmqadmin.dto.QueueInfoDTO;
import com.campuslab.rabbitmqadmin.service.OperacionNoPermitidaException;
import com.campuslab.rabbitmqadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RabbitMqAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService service;

    private static JwtRequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void crearCola_conNombreVacioYSinDurable_responde400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/admin/rabbitmq/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.name").exists())
                .andExpect(jsonPath("$.errores.durable").exists());

        verifyNoInteractions(service);
    }

    @Test
    void crearCola_conPrefijoReservadoAmq_responde400() throws Exception {
        mockMvc.perform(post("/api/admin/rabbitmq/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"amq.mia\",\"durable\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.name").exists());
    }

    @Test
    void crearCola_valida_responde201() throws Exception {
        when(service.crearCola(any())).thenReturn(
                new QueueInfoDTO("q.test", "running", 0, 0, 0, 0, true, false, Map.of()));

        mockMvc.perform(post("/api/admin/rabbitmq/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.test\",\"durable\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("q.test"));
    }

    @Test
    void crearExchange_conTipoInvalido_responde400() throws Exception {
        mockMvc.perform(post("/api/admin/rabbitmq/exchanges").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x.test\",\"type\":\"cualquiera\",\"durable\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.type").exists());

        verifyNoInteractions(service);
    }

    @Test
    void crearBinding_sinRoutingKey_responde400() throws Exception {
        mockMvc.perform(post("/api/admin/rabbitmq/bindings").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"x.test\",\"queue\":\"q.test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.routingKey").exists());
    }

    @Test
    void eliminarColaProtegida_responde409() throws Exception {
        doThrow(new OperacionNoPermitidaException("q.cmd.email es parte de la topologia protegida"))
                .when(service).eliminarCola(eq("q.cmd.email"), anyBoolean());

        mockMvc.perform(delete("/api/admin/rabbitmq/queues/q.cmd.email").with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void sinRolAdmin_responde403() throws Exception {
        mockMvc.perform(post("/api/admin/rabbitmq/queues")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"q.test\",\"durable\":true}"))
                .andExpect(status().isForbidden());

        verify(service, never()).crearCola(any());
    }
}
