package com.campuslab.rabbitmqadmin.service;

import com.campuslab.rabbitmqadmin.client.RabbitMqManagementClient;
import com.campuslab.rabbitmqadmin.dto.BindingInfoDTO;
import com.campuslab.rabbitmqadmin.dto.BindingRequestDTO;
import com.campuslab.rabbitmqadmin.dto.ExchangeInfoDTO;
import com.campuslab.rabbitmqadmin.dto.ExchangeRequestDTO;
import com.campuslab.rabbitmqadmin.dto.OverviewDTO;
import com.campuslab.rabbitmqadmin.dto.QueueInfoDTO;
import com.campuslab.rabbitmqadmin.dto.QueueRequestDTO;
import com.campuslab.rabbitmqadmin.dto.ReprocesoDlqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Logica de negocio para administrar la topologia de RabbitMQ (colas,
 * exchanges y bindings) sobre la Management API.
 *
 * <p>Reglas que aplica, ademas de la validacion de entrada de los DTOs:</p>
 * <ul>
 *   <li>Los recursos listados en campuslab.rabbitmq.protected-resources
 *       (la topologia de la que dependen bookings y notify) no se pueden
 *       modificar ni eliminar desde esta API.</li>
 *   <li>Crear un recurso que ya existe es un conflicto (409): para cambiarlo
 *       se usa PUT.</li>
 *   <li>RabbitMQ no permite cambiar durable/argumentos de una cola o
 *       exchange existente, asi que "actualizar" significa recrearlo
 *       restaurando sus bindings. Una cola solo se recrea si esta vacia,
 *       para no perder mensajes.</li>
 * </ul>
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);
    private static final String SUFIJO_DLQ = ".dlq";

    private final RabbitMqManagementClient client;
    private final Set<String> recursosProtegidos;

    public RabbitAdminService(RabbitMqManagementClient client,
                              @Value("${campuslab.rabbitmq.protected-resources:}") List<String> recursosProtegidos) {
        this.client = client;
        this.recursosProtegidos = new HashSet<>(recursosProtegidos);
    }

    public OverviewDTO overview() {
        return client.overview();
    }

    // ------------------------------------------------------------------ colas

    public List<QueueInfoDTO> listarColas() {
        return client.queues();
    }

    public QueueInfoDTO crearCola(QueueRequestDTO request) {
        if (client.queue(request.name()).isPresent()) {
            throw new OperacionNoPermitidaException(
                    "La cola " + request.name() + " ya existe; usa PUT para actualizarla");
        }
        client.declareQueue(request.name(), definicionCola(request));
        log.info("Cola {} creada (durable={}, argumentos={})",
                request.name(), request.durable(), argumentosCola(request));
        return obtenerCola(request.name());
    }

    public QueueInfoDTO actualizarCola(String name, QueueRequestDTO request) {
        exigirMismoNombre(name, request.name());
        exigirNoProtegido(name);
        QueueInfoDTO actual = obtenerCola(name);

        if (mismaConfiguracion(actual, request)) {
            return actual;
        }
        if (actual.messages() != null && actual.messages() > 0) {
            throw new OperacionNoPermitidaException("La cola " + name + " tiene " + actual.messages()
                    + " mensajes; vaciala antes de cambiar su configuracion para no perderlos");
        }

        List<BindingInfoDTO> bindings = client.queueBindings(name).stream()
                // el binding implicito al default exchange ("") lo crea RabbitMQ solo
                .filter(b -> StringUtils.hasText(b.source()))
                .toList();

        // if-empty=true: si entro un mensaje desde que se leyo el contador,
        // RabbitMQ rechaza el borrado en vez de descartarlo.
        client.deleteQueue(name, true);
        client.declareQueue(name, definicionCola(request));
        bindings.forEach(b -> client.createBinding(b.source(), "q", name, b.routing_key(), b.arguments()));

        log.info("Cola {} recreada con nueva configuracion; {} bindings restaurados", name, bindings.size());
        return obtenerCola(name);
    }

    public void eliminarCola(String name, boolean soloSiEstaVacia) {
        exigirNoProtegido(name);
        obtenerCola(name);
        client.deleteQueue(name, soloSiEstaVacia);
        log.info("Cola {} eliminada (ifEmpty={})", name, soloSiEstaVacia);
    }

    /**
     * Devuelve los mensajes de una DLQ a la cola de la que salieron (la que
     * indica el header x-death, o el nombre sin ".dlq"), para que notify los
     * vuelva a procesar una vez corregida la causa del fallo.
     *
     * <p>Se mueve de a un mensaje: si la cola de origen no lo acepta, se
     * devuelve a la DLQ y se detiene. Solo se procesan los mensajes que habia
     * al empezar, para no reciclar en el mismo pedido uno que vuelva a fallar.</p>
     */
    public ReprocesoDlqDTO reprocesarDlq(String dlq, int maximo) {
        if (!dlq.endsWith(SUFIJO_DLQ)) {
            throw new OperacionNoPermitidaException(
                    "Solo se reprocesan colas de mensajes fallidos (" + SUFIJO_DLQ + "); " + dlq + " no lo es");
        }
        QueueInfoDTO cola = obtenerCola(dlq);
        int pendientes = Math.min(maximo, cola.messages() == null ? 0 : cola.messages().intValue());

        int reprocesados = 0;
        int devueltos = 0;
        for (int i = 0; i < pendientes; i++) {
            List<Map<String, Object>> leidos = client.sacarMensajes(dlq, 1);
            if (leidos.isEmpty()) {
                break;
            }
            Map<String, Object> mensaje = leidos.get(0);
            String origen = colaDeOrigen(mensaje, dlq);
            @SuppressWarnings("unchecked")
            Map<String, Object> propiedades = (Map<String, Object>) mensaje.getOrDefault("properties", Map.of());
            String payload = String.valueOf(mensaje.get("payload"));
            String encoding = String.valueOf(mensaje.getOrDefault("payload_encoding", "string"));

            if (publicarSinFallar(origen, propiedades, payload, encoding)) {
                reprocesados++;
                continue;
            }
            devueltos++;
            if (!publicarSinFallar(dlq, propiedades, payload, encoding)) {
                log.error("No se pudo reenviar a {} ni devolver a {} un mensaje. Cuerpo: {}", origen, dlq, payload);
            }
            break;
        }
        log.info("DLQ {}: {} mensajes devueltos a su cola de origen, {} vueltos a la DLQ", dlq, reprocesados, devueltos);
        return new ReprocesoDlqDTO(dlq, reprocesados, devueltos);
    }

    private boolean publicarSinFallar(String cola, Map<String, Object> propiedades, String payload, String encoding) {
        try {
            // amq.default = exchange por defecto: enruta a la cola con ese nombre
            return client.publicar("amq.default", cola, propiedades, payload, encoding);
        } catch (RuntimeException ex) {
            log.warn("Fallo al publicar en {}: {}", cola, ex.getMessage());
            return false;
        }
    }

    private static String colaDeOrigen(Map<String, Object> mensaje, String dlq) {
        Object propiedades = mensaje.get("properties");
        if (propiedades instanceof Map<?, ?> props && props.get("headers") instanceof Map<?, ?> headers
                && headers.get("x-death") instanceof List<?> muertes && !muertes.isEmpty()
                && muertes.get(0) instanceof Map<?, ?> primera && primera.get("queue") instanceof String cola
                && StringUtils.hasText(cola)) {
            return cola;
        }
        return dlq.substring(0, dlq.length() - SUFIJO_DLQ.length());
    }

    // -------------------------------------------------------------- exchanges

    public List<ExchangeInfoDTO> listarExchanges() {
        return client.exchanges();
    }

    public ExchangeInfoDTO crearExchange(ExchangeRequestDTO request) {
        if (client.exchange(request.name()).isPresent()) {
            throw new OperacionNoPermitidaException(
                    "El exchange " + request.name() + " ya existe; usa PUT para actualizarlo");
        }
        client.declareExchange(request.name(), definicionExchange(request));
        log.info("Exchange {} creado (type={}, durable={})", request.name(), request.type(), request.durable());
        return obtenerExchange(request.name());
    }

    public ExchangeInfoDTO actualizarExchange(String name, ExchangeRequestDTO request) {
        exigirMismoNombre(name, request.name());
        exigirNoProtegido(name);
        ExchangeInfoDTO actual = obtenerExchange(name);

        if (mismaConfiguracion(actual, request)) {
            return actual;
        }

        List<BindingInfoDTO> comoOrigen = client.exchangeBindingsAsSource(name);
        List<BindingInfoDTO> comoDestino = client.exchangeBindingsAsDestination(name);

        client.deleteExchange(name, false);
        client.declareExchange(name, definicionExchange(request));
        comoOrigen.forEach(b -> client.createBinding(name, tipoDestino(b), b.destination(), b.routing_key(), b.arguments()));
        comoDestino.forEach(b -> client.createBinding(b.source(), "e", name, b.routing_key(), b.arguments()));

        log.info("Exchange {} recreado como {}; {} bindings restaurados",
                name, request.type(), comoOrigen.size() + comoDestino.size());
        return obtenerExchange(name);
    }

    public void eliminarExchange(String name, boolean soloSiNoSeUsa) {
        exigirNoProtegido(name);
        obtenerExchange(name);
        client.deleteExchange(name, soloSiNoSeUsa);
        log.info("Exchange {} eliminado (ifUnused={})", name, soloSiNoSeUsa);
    }

    // --------------------------------------------------------------- bindings

    public List<BindingInfoDTO> listarBindings() {
        return client.bindings();
    }

    public BindingInfoDTO crearBinding(BindingRequestDTO request) {
        obtenerExchange(request.exchange());
        obtenerCola(request.queue());
        if (buscarBinding(request.exchange(), request.queue(), request.routingKey()) != null) {
            throw new OperacionNoPermitidaException("Ya existe el binding " + request.exchange() + " -> "
                    + request.queue() + " con routingKey '" + request.routingKey() + "'");
        }
        client.createBinding(request.exchange(), "q", request.queue(), request.routingKey(), Map.of());
        log.info("Binding {} -> {} creado (routingKey='{}')", request.exchange(), request.queue(), request.routingKey());
        return buscarBinding(request.exchange(), request.queue(), request.routingKey());
    }

    public void eliminarBinding(String exchange, String queue, String routingKey) {
        if (esProtegido(exchange) && esProtegido(queue)) {
            throw new OperacionNoPermitidaException("El binding " + exchange + " -> " + queue
                    + " es parte de la topologia protegida de CampusLab y no se puede eliminar");
        }
        BindingInfoDTO binding = buscarBinding(exchange, queue, routingKey);
        if (binding == null) {
            throw new RecursoNoEncontradoException("No existe el binding " + exchange + " -> " + queue
                    + " con routingKey '" + routingKey + "'");
        }
        client.deleteBinding(exchange, queue, binding.properties_key());
        log.info("Binding {} -> {} eliminado (routingKey='{}')", exchange, queue, routingKey);
    }

    // ---------------------------------------------------------------- helpers

    private QueueInfoDTO obtenerCola(String name) {
        return client.queue(name)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cola " + name));
    }

    private ExchangeInfoDTO obtenerExchange(String name) {
        return client.exchange(name)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el exchange " + name));
    }

    private BindingInfoDTO buscarBinding(String exchange, String queue, String routingKey) {
        return client.bindingsBetween(exchange, queue).stream()
                .filter(b -> Objects.equals(b.routing_key(), routingKey))
                .findFirst()
                .orElse(null);
    }

    private boolean esProtegido(String name) {
        return recursosProtegidos.contains(name);
    }

    private void exigirNoProtegido(String name) {
        if (!StringUtils.hasText(name) || name.startsWith("amq.")) {
            throw new OperacionNoPermitidaException("Los recursos internos de RabbitMQ (amq.*) no se pueden modificar");
        }
        if (esProtegido(name)) {
            throw new OperacionNoPermitidaException(name
                    + " es parte de la topologia protegida de CampusLab y no se puede modificar ni eliminar desde esta API");
        }
    }

    private void exigirMismoNombre(String enRuta, String enCuerpo) {
        if (!enRuta.equals(enCuerpo)) {
            throw new IllegalArgumentException("El nombre del cuerpo (" + enCuerpo
                    + ") no coincide con el de la URL (" + enRuta + "); RabbitMQ no permite renombrar recursos");
        }
    }

    private Map<String, Object> definicionCola(QueueRequestDTO request) {
        Map<String, Object> definicion = new LinkedHashMap<>();
        definicion.put("durable", request.durable());
        definicion.put("auto_delete", Boolean.TRUE.equals(request.autoDelete()));
        definicion.put("arguments", argumentosCola(request));
        return definicion;
    }

    private Map<String, Object> argumentosCola(QueueRequestDTO request) {
        Map<String, Object> argumentos = new LinkedHashMap<>();
        if (StringUtils.hasText(request.deadLetterExchange())) {
            argumentos.put("x-dead-letter-exchange", request.deadLetterExchange());
        }
        if (StringUtils.hasText(request.deadLetterRoutingKey())) {
            argumentos.put("x-dead-letter-routing-key", request.deadLetterRoutingKey());
        }
        if (request.messageTtl() != null) {
            argumentos.put("x-message-ttl", request.messageTtl());
        }
        if (request.maxLength() != null) {
            argumentos.put("x-max-length", request.maxLength());
        }
        return argumentos;
    }

    private Map<String, Object> definicionExchange(ExchangeRequestDTO request) {
        Map<String, Object> definicion = new LinkedHashMap<>();
        definicion.put("type", request.type());
        definicion.put("durable", request.durable());
        definicion.put("auto_delete", Boolean.TRUE.equals(request.autoDelete()));
        definicion.put("internal", Boolean.TRUE.equals(request.internal()));
        definicion.put("arguments", Map.of());
        return definicion;
    }

    private boolean mismaConfiguracion(QueueInfoDTO actual, QueueRequestDTO request) {
        return Objects.equals(actual.durable(), request.durable())
                && Objects.equals(Boolean.TRUE.equals(actual.auto_delete()), Boolean.TRUE.equals(request.autoDelete()))
                && normalizar(actual.arguments()).equals(normalizar(argumentosCola(request)));
    }

    private boolean mismaConfiguracion(ExchangeInfoDTO actual, ExchangeRequestDTO request) {
        return Objects.equals(actual.type(), request.type())
                && Objects.equals(actual.durable(), request.durable())
                && Objects.equals(Boolean.TRUE.equals(actual.auto_delete()), Boolean.TRUE.equals(request.autoDelete()))
                && Objects.equals(Boolean.TRUE.equals(actual.internal()), Boolean.TRUE.equals(request.internal()));
    }

    /** Compara argumentos por su texto: RabbitMQ devuelve 60000 como Integer o Long segun el caso. */
    private Map<String, String> normalizar(Map<String, Object> argumentos) {
        if (argumentos == null) {
            return Map.of();
        }
        return argumentos.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
    }

    private String tipoDestino(BindingInfoDTO binding) {
        return "exchange".equals(binding.destination_type()) ? "e" : "q";
    }
}
