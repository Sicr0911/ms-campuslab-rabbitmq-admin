# ms-campuslab-rabbitmq-admin

Microservicio administrador de RabbitMQ de CampusLab. Consulta el estado del
cluster y administra **colas, exchanges y bindings** a través de la
Management API de RabbitMQ (puerto 15672).

- **Puerto:** 8086 (vía BFF: `/api/admin/rabbitmq/**`)
- **Seguridad:** JWT obligatorio con rol `ADMIN` en todos los endpoints.
- **Stack:** Spring Boot 3.3, Java 17, Spring Security (OAuth2 Resource Server), Bean Validation.

## Arquitectura

```
RabbitMqAdminController   -> valida la entrada (@Valid) y traduce a HTTP
        │
RabbitAdminService        -> reglas de negocio (recursos protegidos, recrear sin perder
        │                    bindings, no recrear colas con mensajes, duplicados)
RabbitMqManagementClient  -> cliente HTTP de la Management API (sin lógica de negocio)
```

El controlador no conoce detalles de RabbitMQ: solo llama a métodos de alto
nivel de `RabbitAdminService`.

## Endpoints

Base: `/api/admin/rabbitmq`

### Estado

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| GET | `/overview` | Versión, totales de mensajes, colas, conexiones y consumidores | 200 |

### Colas

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/queues` | Lista las colas del vhost | 200 |
| POST | `/queues` | Crea una cola | 201, 400, 409 |
| PUT | `/queues/{name}` | Actualiza una cola (la recrea conservando sus bindings; solo si está vacía) | 200, 400, 404, 409 |
| DELETE | `/queues/{name}?ifEmpty=true` | Elimina una cola (con `ifEmpty=true`, solo si no tiene mensajes) | 204, 404, 409 |

### Exchanges

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/exchanges` | Lista los exchanges del vhost | 200 |
| POST | `/exchanges` | Crea un exchange | 201, 400, 409 |
| PUT | `/exchanges/{name}` | Actualiza un exchange (lo recrea restaurando sus bindings) | 200, 400, 404, 409 |
| DELETE | `/exchanges/{name}?ifUnused=true` | Elimina un exchange (con `ifUnused=true`, solo si no tiene bindings) | 204, 404, 409 |

### Bindings

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| GET | `/bindings` | Lista los bindings del vhost | 200 |
| POST | `/bindings` | Enlaza un exchange con una cola | 201, 400, 404, 409 |
| DELETE | `/bindings?exchange=&queue=&routingKey=` | Elimina un binding | 204, 400, 404, 409 |

## Ejemplos

### Crear una cola con DLQ y TTL

```http
POST /api/admin/rabbitmq/queues
Authorization: Bearer <jwt ADMIN>
Content-Type: application/json

{
  "name": "q.reportes",
  "durable": true,
  "deadLetterExchange": "cmd.dlx",
  "deadLetterRoutingKey": "q.reportes.dlq",
  "messageTtl": 60000
}
```

### Crear un exchange

```http
POST /api/admin/rabbitmq/exchanges
Content-Type: application/json

{ "name": "x.reportes", "type": "topic", "durable": true }
```

### Crear un binding

```http
POST /api/admin/rabbitmq/bindings
Content-Type: application/json

{ "exchange": "x.reportes", "queue": "q.reportes", "routingKey": "reporte.*" }
```

## Validación de entrada

Los cuerpos se validan con Bean Validation (`QueueRequestDTO`,
`ExchangeRequestDTO`, `BindingRequestDTO`):

| Campo | Regla |
|---|---|
| `name`, `exchange`, `queue` | Obligatorios, máx. 255 caracteres, solo letras, números, `.` `_` `:` `-`, y no pueden comenzar con `amq.` (reservado por RabbitMQ) |
| `durable` | Obligatorio (no se puede cambiar sin recrear el recurso) |
| `type` | Obligatorio: `direct`, `fanout`, `topic` o `headers` |
| `routingKey` | Obligatorio (puede ser `""` para exchanges fanout) |
| `messageTtl`, `maxLength` | Opcionales, mayores que 0 |

Ejemplo de respuesta 400:

```json
{
  "message": "Solicitud invalida",
  "errores": {
    "name": "el nombre de la cola es obligatorio",
    "durable": "durable es obligatorio"
  }
}
```

## Códigos de error

| Código | Cuándo |
|---|---|
| 400 | Entrada inválida, o el nombre del cuerpo no coincide con el de la URL en un PUT |
| 401 / 403 | Sin JWT o sin rol `ADMIN` |
| 404 | La cola, el exchange o el binding no existe |
| 409 | El recurso ya existe, está protegido, la cola tiene mensajes, o RabbitMQ rechazó la operación |
| 502 | No se pudo contactar la Management API de RabbitMQ |

## Recursos protegidos

La topología de la que dependen bookings, catalog y notify (`cmd.direct`,
`cmd.topic`, `cmd.dlx`, `q.cmd.*` y sus DLQ) no se puede modificar ni
eliminar desde esta API. La lista se configura en
`campuslab.rabbitmq.protected-resources` (`application.yml`).

## Configuración

| Variable | Descripción | Default |
|---|---|---|
| `RABBITMQ_HOST` | Host del broker (instancia `ec2-mq`) | `100.26.43.133` |
| `RABBITMQ_MANAGEMENT_PORT` | Puerto de la Management API | `15672` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Credenciales de administración (RabbitMQ bloquea `guest` en conexiones remotas) | `admin` / `admin_dev` |
| `RABBITMQ_VHOST` | Virtual host | `/` |
| `AUTH_ISSUER_URI` | Issuer de Azure AD que firma los JWT | `https://login.microsoftonline.com/<tenant>/v2.0` |

> En AWS Academy la IP pública de `ec2-mq` cambia cada vez que se reinicia el
> lab. En EC2 conviene definir `RABBITMQ_HOST` con la **IP privada** de
> `ec2-mq`, que no cambia (ver `infra/aws/README.md` en MS-Campuslab-BFF).

## Ejecutar y probar

```bash
mvn spring-boot:run
mvn test   # 18 tests: contexto, validacion HTTP, seguridad y RabbitAdminService
```
