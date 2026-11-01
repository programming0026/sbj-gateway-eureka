# API Gateway con Spring Cloud Gateway y Eureka

Ejemplo con **Spring Boot 4**, **Java 21** y **Spring Cloud 2025.1** de una arquitectura de microservicios completa: un **servidor Eureka** (registro de servicios), un **API Gateway** como única puerta de entrada y **dos APIs** (productos y pedidos) que se registran en Eureka y se consumen a través del gateway. Es un proyecto Maven de varios módulos y también se levanta con Docker Compose.

## La arquitectura

```text
                                   ┌─────────────────────┐
                                   │  discovery-server   │  Eureka :8761
                                   │  (registro/agenda)  │
                                   └─────────▲───────────┘
              se registran y envían latidos  │  consultan quién está disponible
        ┌───────────────┬───────────────┬────┴──────────┐
        │               │               │               │
 ┌──────┴─────┐  ┌──────┴─────┐  ┌──────┴──────┐ ┌──────┴──────┐
 │ product-   │  │ product-   │  │ order-      │ │ api-gateway │ ◄── cliente (navegador, app, curl)
 │ service    │  │ service    │  │ service     │ │   :8080     │      SOLO conoce esta dirección
 │ copia 1    │  │ copia 2    │  │             │ └──────┬──────┘
 └────────────┘  └────────────┘  └──────┬──────┘        │ /api/products/** → lb://product-service (reparte entre copias)
        ▲                               │               │ /api/orders/**   → lb://order-service
        └───────── reserva stock ───────┘ (order-service llama a product-service por su NOMBRE)
```

| Módulo | Puerto | Qué hace |
|--------|--------|----------|
| `discovery-server` | 8761 | **Eureka**: cada servicio se registra con su nombre y dirección, envía latidos, y se expulsa si deja de hacerlo |
| `product-service` | 8081 | API de productos (catálogo en memoria). Se pueden levantar varias copias |
| `order-service` | 8082 | API de pedidos. **Consume** a `product-service` usando su nombre, con balanceo de carga |
| `api-gateway` | 8080 | Spring Cloud Gateway: enruta, balancea y aplica reglas comunes (clave de API, límite de peticiones, CORS, reintentos, identificador de petición) |

## ¿Por qué un gateway y por qué Eureka?

- **Sin gateway** cada cliente tendría que conocer la dirección de cada servicio, repetir la autenticación y el CORS en todos, y romperse cada vez que algo cambie de lugar.
- **Sin Eureka** habría que escribir direcciones IP y puertos en la configuración, y no se podrían añadir ni quitar copias dinámicamente.
- **Juntos:** el gateway apunta a `lb://product-service` ("cualquier copia viva de este servicio"); Eureka sabe cuáles hay; el balanceador reparte. Las copias entran y salen sin tocar nada.

## Las rutas del gateway

```java
.route("product-service", r -> r.path("/api/products/**")
        .filters(f -> f.rewritePath("/api/(?<segment>.*)", "/${segment}")   // /api/products/1 -> /products/1
                       .retry(...solo GET, en otra copia...))
        .uri("lb://product-service"))                                       // destino por NOMBRE, no por dirección
```

Los servicios no saben nada del prefijo `/api` ni del gateway: exponen `/products` y `/orders` como cualquier API.

### Filtros globales (se aplican a todas las rutas, en este orden)

| Filtro | Qué hace |
|--------|----------|
| `RequestIdFilter` | Garantiza un `X-Request-Id` en cada petición (conserva el del cliente si es seguro; si no, genera uno). Viaja a los servicios y vuelve en la respuesta para rastrear una petición por todo el sistema |
| `LoggingFilter` | Registra método, ruta, servicio destino, código y duración |
| `RateLimitFilter` | Máximo de peticiones por minuto por cliente (clave de API o IP); responde `429` con `Retry-After` |
| `ApiKeyFilter` | Las lecturas (GET) son públicas; las operaciones que modifican datos exigen `X-Api-Key` (comparación en tiempo constante). Las peticiones rechazadas **nunca llegan al servicio** |

Además: **CORS** en un solo lugar, **reintentos** de los GET que reciben 502/503 (nunca de los POST: repetirlos podría duplicar datos; cada reintento va a otra copia), y el endpoint `/gateway/services` que muestra qué copias ve el gateway.

## Cómo funciona Eureka (y por qué hay retrasos)

1. Al arrancar, cada servicio se **registra** (`POST /eureka/apps/...`) con su nombre, IP y puerto.
2. Envía un **latido** cada 5 s. Si pasan 15 s sin latidos, Eureka lo **expulsa**.
3. Los clientes **descargan el registro** y lo guardan en memoria; lo refrescan cada 5 s. El balanceador guarda además su propia caché de copias (5 s).

Es **eventualmente consistente**: tras apagar una copia pasan unos 15–25 s hasta que todos dejan de enviarle tráfico. Por eso el gateway **reintenta las lecturas** en otra copia, y `order-service` reintenta si la conexión es rechazada: durante esa ventana el sistema sigue respondiendo.

> Valores pensados para ver los cambios en segundos. Por defecto Eureka usa 30 s de latido, 90 s de expulsión y 30 s de refresco: cámbialos en producción según tu tolerancia.

## Trampas reales que este ejemplo evita (y encontró probándolo)

| Problema | Causa y solución |
|----------|------------------|
| `order-service` **nunca se registraba** en Eureka (`BeanCurrentlyInCreationException`) | El cliente de Eureka usa el `RestClient.Builder` que encuentre en el contexto. Si declaras un `@LoadBalanced RestClient.Builder` como bean, Eureka intenta "balancear" la llamada a su propio servidor: un ciclo. Se resuelve añadiendo el interceptor de balanceo **a un solo cliente** y no exponiendo ningún builder con balanceo |
| Los servicios aparecen **DOWN** hasta 30–40 s después de arrancar | Se registran antes de estar listos y, con `healthcheck.enabled=true`, Eureka replica el cambio de estado cada 30 s. Se acorta con `instance-info-replication-interval-seconds=5` |
| Con Docker, las réplicas se pisaban o eran inalcanzables | `prefer-ip-address=true` e `instance-id=servicio:ip:puerto`: el id debe ser único por copia y la dirección alcanzable desde los demás contenedores |
| Tras apagar una copia, aún llegan peticiones a ella unos segundos | Es la consistencia eventual de arriba: reintentos en el gateway y en `order-service` |
| En Spring Boot 4 el endpoint `gateway` del actuator no aparecía | Hay que declarar su acceso: `management.endpoint.gateway.access=read-only` |
| La lista `/eureka/apps` no refleja una baja al instante | La sirve una caché de solo lectura (refresco de 3 s en este ejemplo, 30 s por defecto); la consulta de una instancia concreta sí es inmediata |

## Estructura

```text
sbj-gateway-eureka/
├── pom.xml                       # padre: Spring Boot 4.0.1 + Spring Cloud 2025.1.0, 4 módulos
├── docker-compose.yml            # todo el sistema (2 réplicas de product-service)
├── discovery-server/             # @EnableEurekaServer
├── product-service/
│   ├── controller/               # ProductController
│   ├── model/                    # Product
│   ├── repository/               # ProductStore (reserva atómica de stock)
│   └── filter/                   # ServedByFilter
├── order-service/
│   ├── controller/               # OrderController
│   ├── service/                  # OrderService (RestClient con balanceo hacia product-service)
│   ├── config/                   # HttpClientConfig
│   └── filter/                   # ServedByFilter
└── api-gateway/
    ├── config/GatewayRoutes.java       # rutas lb:// con rewritePath y reintentos
    ├── config/CorsConfig, GatewayProperties
    ├── filter/                          # RequestId, Logging, RateLimit, ApiKey
    └── controller/ServicesController.java   # /gateway/services
```

## Requisitos

- JDK 21 y Maven 3.9 o superior. Docker (solo para el modo con contenedores).

## Ejecutar sin Docker

```bash
mvn clean package -DskipTests

java -jar discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar                       # 1.º: Eureka  http://localhost:8761
java -jar product-service/target/product-service-0.0.1-SNAPSHOT.jar                         # copia 1 (8081)
java -jar product-service/target/product-service-0.0.1-SNAPSHOT.jar --server.port=8083     # copia 2
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar                                 # único punto de entrada :8080
```

Abre <http://localhost:8761>: verás `API-GATEWAY`, `ORDER-SERVICE` y las dos copias de `PRODUCT-SERVICE`. Los servicios tardan unos segundos en pasar a UP.

## Ejecutar con Docker Compose

```bash
mvn clean package -DskipTests
docker compose up --build            # si el 8080 está ocupado:  GATEWAY_PORT=18080 docker compose up --build
```

Solo se publica el puerto del gateway (y el panel de Eureka): los servicios son inalcanzables desde fuera, que es como debe ser en producción.

## Probar

```bash
curl -i http://localhost:8080/api/products                 # lee X-Served-By: cambia entre las copias
curl -i http://localhost:8080/api/products/1

# Escribir exige clave de API (cabecera X-Api-Key)
curl -i -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{"productId":2,"quantity":3}'     # 401
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -H "X-Api-Key: demo-key-123" \
     -d '{"productId":2,"quantity":3}'      # 201: reservedBy muestra qué copia de productos atendió la reserva
curl http://localhost:8080/api/orders
curl http://localhost:8080/gateway/services                # lo que el gateway ve en Eureka
curl http://localhost:8080/actuator/gateway/routes         # las rutas configuradas
```

**Experimentos:**

1. Haz varios `GET /api/products` y observa `X-Served-By` alternarse (balanceo round robin).
2. Apaga una copia de `product-service`: las lecturas siguen devolviendo 200 al instante (reintentos); en ~20 s Eureka la expulsa y `/gateway/services` deja de listarla. Vuélvela a levantar y reaparece sola.
3. Apaga las dos copias: `GET /api/products` responde `503` con un JSON claro; `GET /api/orders` sigue funcionando (no depende de productos).
4. Crea un pedido de más unidades que el stock: `409`; de un producto inexistente: `404`.
5. Supera las `gateway.rate-limit-per-minute` peticiones: `429` con `Retry-After`.

## Pruebas

```bash
mvn verify
```

28 pruebas, y además el sistema se verificó **de extremo a extremo con los 5 procesos reales y con Docker Compose**:

- **`discovery-server` (2):** el servidor Eureka real con su protocolo REST: registrar, consultar, latido y baja.
- **`product-service` (5):** API, validaciones y 50 reservas simultáneas sobre un stock de 20 (exactamente 20 tienen éxito).
- **`order-service` (7):** con dos copias simuladas de productos: cálculo del total, **balanceo** entre copias, traducción de errores (404, 409, 503), **servicio sin copias** (503), y **una copia muerta** en la lista del balanceador (los 20 pedidos salen bien y ninguna reserva se duplica). Una prueba de regresión garantiza que no se exponga un `RestClient.Builder` con balanceo.
- **`api-gateway` (14):** ruteo con reescritura de ruta, balanceo entre copias, `X-Request-Id`, clave de API (las rechazadas no llegan al servicio), **reintentos de lecturas en otra copia** y no reintento de escrituras, tiempo máximo (504), servicio sin copias (503), ruta inexistente (404), CORS, límite de peticiones por cliente (429) y el actuator.

En las pruebas de `order-service` y `api-gateway` el directorio de servicios es la configuración estática de Spring Cloud (`SimpleDiscoveryClient`) en lugar de Eureka; las rutas `lb://` y el balanceador son los mismos. El comportamiento con Eureka real se comprobó arrancando los procesos.

## Para producción

- Los **servicios no deben ser alcanzables sin pasar por el gateway** (red privada): toda la seguridad del gateway se salta si alguien los llama directamente.
- Sustituye la clave de API por **JWT/OAuth2** (resource server en el gateway) y guarda los secretos en un gestor de secretos.
- El límite de peticiones de este ejemplo es en memoria de **una** instancia; con varias usa el filtro `RequestRateLimiter` con Redis.
- Añade **circuit breaker** (Resilience4j) para dejar de llamar a un servicio que falla y responder con una alternativa.
- Monta **varios servidores Eureka** que se repliquen entre sí, protégelos con autenticación y revisa la "autopreservación" (aquí está desactivada para desarrollo).
- Centraliza la configuración (Spring Cloud Config) y la observabilidad (trazas distribuidas con Micrometer Tracing: el `X-Request-Id` es el primer paso).
- En Kubernetes, el propio clúster ofrece descubrimiento y balanceo (Services/Ingress): Eureka es más habitual fuera de Kubernetes.

## Licencia

Código de ejemplo de uso libre con fines educativos.
