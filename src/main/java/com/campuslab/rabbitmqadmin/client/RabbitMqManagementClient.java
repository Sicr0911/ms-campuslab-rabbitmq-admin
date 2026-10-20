package com.campuslab.rabbitmqadmin.client;

import com.campuslab.rabbitmqadmin.dto.BindingInfoDTO;
import com.campuslab.rabbitmqadmin.dto.ExchangeInfoDTO;
import com.campuslab.rabbitmqadmin.dto.OverviewDTO;
import com.campuslab.rabbitmqadmin.dto.QueueInfoDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cliente HTTP hacia la Management API de RabbitMQ (puerto 15672, la misma
 * UI que "management" expone como REST). Este microservicio no se conecta
 * al cluster por AMQP: consulta y modifica su topologia via HTTP.
 *
 * <p>Es una capa delgada de transporte: traduce cada operacion a su
 * endpoint de la Management API y los errores HTTP a excepciones propias.
 * Las reglas de negocio (recursos protegidos, recrear sin perder bindings,
 * etc.) viven en RabbitAdminService.</p>
 */
@Component
public class RabbitMqManagementClient {

    private static final ParameterizedTypeReference<List<QueueInfoDTO>> LISTA_COLAS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<ExchangeInfoDTO>> LISTA_EXCHANGES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<BindingInfoDTO>> LISTA_BINDINGS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final String vhost;

    public RabbitMqManagementClient(
            @Value("${campuslab.rabbitmq.management-url}") String managementUrl,
            @Value("${campuslab.rabbitmq.management-username}") String username,
            @Value("${campuslab.rabbitmq.management-password}") String password,
            @Value("${campuslab.rabbitmq.vhost}") String vhost) {

        String credenciales = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));

        // TEMPLATE_AND_VALUES codifica tambien los "/" de las variables: el
        // vhost por defecto "/" debe viajar como %2F en la URL.
        DefaultUriBuilderFactory uriBuilderFactory = new DefaultUriBuilderFactory(managementUrl);
        uriBuilderFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES);

        this.vhost = vhost;
        this.restClient = RestClient.builder()
                .uriBuilderFactory(uriBuilderFactory)
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

    // ------------------------------------------------------------------ colas

    public List<QueueInfoDTO> queues() {
        try {
            return restClient.get()
                    .uri("/api/queues/{vhost}", vhost)
                    .retrieve()
                    .body(LISTA_COLAS);
        } catch (RestClientException ex) {
            throw new RabbitMqManagementException("No fue posible consultar las colas de RabbitMQ", ex);
        }
    }

    public Optional<QueueInfoDTO> queue(String name) {
        return buscar("la cola " + name, () -> restClient.get()
                .uri("/api/queues/{vhost}/{name}", vhost, name)
                .retrieve()
                .body(QueueInfoDTO.class));
    }

    public List<BindingInfoDTO> queueBindings(String name) {
        return listar("los bindings de la cola " + name, () -> restClient.get()
                .uri("/api/queues/{vhost}/{name}/bindings", vhost, name)
                .retrieve()
                .body(LISTA_BINDINGS));
    }

    public void declareQueue(String name, Map<String, Object> definicion) {
        ejecutar("crear la cola " + name, () -> restClient.put()
                .uri("/api/queues/{vhost}/{name}", vhost, name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(definicion)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteQueue(String name, boolean soloSiEstaVacia) {
        ejecutar("eliminar la cola " + name, () -> restClient.delete()
                .uri("/api/queues/{vhost}/{name}?if-empty={ifEmpty}", vhost, name, soloSiEstaVacia)
                .retrieve()
                .toBodilessEntity());
    }

    // -------------------------------------------------------------- exchanges

    public List<ExchangeInfoDTO> exchanges() {
        return listar("los exchanges", () -> restClient.get()
                .uri("/api/exchanges/{vhost}", vhost)
                .retrieve()
                .body(LISTA_EXCHANGES));
    }

    public Optional<ExchangeInfoDTO> exchange(String name) {
        return buscar("el exchange " + name, () -> restClient.get()
                .uri("/api/exchanges/{vhost}/{name}", vhost, name)
                .retrieve()
                .body(ExchangeInfoDTO.class));
    }

    /** Bindings donde este exchange es el origen. */
    public List<BindingInfoDTO> exchangeBindingsAsSource(String name) {
        return listar("los bindings del exchange " + name, () -> restClient.get()
                .uri("/api/exchanges/{vhost}/{name}/bindings/source", vhost, name)
                .retrieve()
                .body(LISTA_BINDINGS));
    }

    /** Bindings exchange-a-exchange donde este exchange es el destino. */
    public List<BindingInfoDTO> exchangeBindingsAsDestination(String name) {
        return listar("los bindings hacia el exchange " + name, () -> restClient.get()
                .uri("/api/exchanges/{vhost}/{name}/bindings/destination", vhost, name)
                .retrieve()
                .body(LISTA_BINDINGS));
    }

    public void declareExchange(String name, Map<String, Object> definicion) {
        ejecutar("crear el exchange " + name, () -> restClient.put()
                .uri("/api/exchanges/{vhost}/{name}", vhost, name)
                .contentType(MediaType.APPLICATION_JSON)
                .body(definicion)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteExchange(String name, boolean soloSiNoSeUsa) {
        ejecutar("eliminar el exchange " + name, () -> restClient.delete()
                .uri("/api/exchanges/{vhost}/{name}?if-unused={ifUnused}", vhost, name, soloSiNoSeUsa)
                .retrieve()
                .toBodilessEntity());
    }

    // --------------------------------------------------------------- bindings

    public List<BindingInfoDTO> bindings() {
        return listar("los bindings", () -> restClient.get()
                .uri("/api/bindings/{vhost}", vhost)
                .retrieve()
                .body(LISTA_BINDINGS));
    }

    public List<BindingInfoDTO> bindingsBetween(String exchange, String queue) {
        return listar("los bindings entre " + exchange + " y " + queue, () -> restClient.get()
                .uri("/api/bindings/{vhost}/e/{exchange}/q/{queue}", vhost, exchange, queue)
                .retrieve()
                .body(LISTA_BINDINGS));
    }

    /**
     * @param tipoDestino "q" (cola) o "e" (exchange), segun la Management API
     */
    public void createBinding(String exchange, String tipoDestino, String destino,
                              String routingKey, Map<String, Object> argumentos) {
        ejecutar("crear el binding " + exchange + " -> " + destino, () -> restClient.post()
                .uri("/api/bindings/{vhost}/e/{exchange}/{tipo}/{destino}", vhost, exchange, tipoDestino, destino)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "routing_key", routingKey,
                        "arguments", argumentos == null ? Map.of() : argumentos))
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteBinding(String exchange, String queue, String propertiesKey) {
        ejecutar("eliminar el binding " + exchange + " -> " + queue, () -> restClient.delete()
                .uri("/api/bindings/{vhost}/e/{exchange}/q/{queue}/{props}", vhost, exchange, queue, propertiesKey)
                .retrieve()
                .toBodilessEntity());
    }

    // ---------------------------------------------------------------- helpers

    private <T> Optional<T> buscar(String recurso, Supplier<T> llamada) {
        try {
            return Optional.ofNullable(llamada.get());
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new RabbitMqManagementException("No fue posible consultar " + recurso + " en RabbitMQ", ex);
        }
    }

    private <T> List<T> listar(String recurso, Supplier<List<T>> llamada) {
        try {
            List<T> resultado = llamada.get();
            return resultado == null ? List.of() : resultado;
        } catch (RestClientException ex) {
            throw new RabbitMqManagementException("No fue posible consultar " + recurso + " en RabbitMQ", ex);
        }
    }

    private void ejecutar(String accion, Runnable llamada) {
        try {
            llamada.run();
        } catch (HttpClientErrorException ex) {
            // 4xx de la Management API: el broker entendio la peticion pero
            // la rechazo (ej. PRECONDITION_FAILED, cola con mensajes y
            // if-empty=true, etc.).
            throw new RabbitMqRechazoException(
                    "RabbitMQ rechazo " + accion + ": " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new RabbitMqManagementException("No fue posible " + accion + " en RabbitMQ", ex);
        }
    }
}
