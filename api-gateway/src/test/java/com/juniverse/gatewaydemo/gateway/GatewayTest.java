package com.juniverse.gatewaydemo.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El gateway real, con DOS copias simuladas de product-service (una sana "A" y otra "B") y una sola de order-service. En lugar de Eureka se
 * usa el directorio estático de Spring Cloud ({@code SimpleDiscoveryClient}): las rutas {@code lb://...} y el balanceador son los mismos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "gateway.rate-limit-per-minute=1000",
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=1s"})
@DisplayName("API Gateway")
class GatewayTest {

    private static StubService productA;
    private static StubService productB;
    private static StubService orders;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) throws IOException {
        productA = new StubService("product-A", false);
        productB = new StubService("product-B", true);   // la B responde 503 en /products/flaky
        orders = new StubService("order-1", false);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", productA::uri);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[1].uri", productB::uri);
        registry.add("spring.cloud.discovery.client.simple.instances.order-service[0].uri", orders::uri);
    }

    @AfterAll
    static void stop() {
        productA.close();
        productB.close();
        orders.close();
    }

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    void clear() {
        for (StubService stub : new StubService[] {productA, productB, orders}) {
            stub.hits.set(0);
            stub.received.clear();
        }
    }

    private HttpResponse<String> call(String method, String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        headers.forEach(builder::header);
        builder.method(method, method.equals("POST") ? HttpRequest.BodyPublishers.ofString("{\"x\":1}") : HttpRequest.BodyPublishers.noBody());
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return call("GET", path, Map.of());
    }

    @Test
    @DisplayName("enruta /api/products/** a product-service reescribiendo la ruta a /products/**")
    void routesAndRewritesThePath() throws Exception {
        HttpResponse<String> response = get("/api/products/42");
        assertEquals(200, response.statusCode());
        assertEquals("product-service", response.headers().firstValue("X-Gateway-Route").orElse(null));
        assertTrue(response.body().contains("\"path\":\"/products/42\""), response.body());
    }

    @Test
    @DisplayName("enruta /api/orders/** a order-service, un servicio distinto")
    void routesToTheOtherService() throws Exception {
        HttpResponse<String> response = get("/api/orders/7");
        assertEquals(200, response.statusCode());
        assertEquals("order-service", response.headers().firstValue("X-Gateway-Route").orElse(null));
        assertEquals("order-1", response.headers().firstValue("X-Served-By").orElse(null));
        assertEquals(0, productA.hits.get() + productB.hits.get(), "no se toca product-service");
    }

    @Test
    @DisplayName("reparte las peticiones entre las dos copias (balanceo de carga round robin)")
    void loadBalances() throws Exception {
        Set<String> served = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            served.add(get("/api/products/1").headers().firstValue("X-Served-By").orElse("?"));
        }
        assertEquals(Set.of("product-A", "product-B"), served);
        assertTrue(productA.hits.get() >= 4 && productB.hits.get() >= 4, "A=" + productA.hits + " B=" + productB.hits);
    }

    @Test
    @DisplayName("X-Request-Id: se genera si falta, se conserva si el cliente lo envía, llega al servicio y vuelve en la respuesta; uno malicioso se reemplaza")
    void requestId() throws Exception {
        HttpResponse<String> generated = get("/api/products/1");
        String id = generated.headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(36, id.length());
        StubService served = productA.received.isEmpty() ? productB : productA;
        assertEquals(id, served.received.get(0).headers().get("x-request-id"));

        HttpResponse<String> own = call("GET", "/api/products/1", Map.of("X-Request-Id", "mi-id-123"));
        assertEquals("mi-id-123", own.headers().firstValue("X-Request-Id").orElse(null));

        HttpResponse<String> hostile = call("GET", "/api/products/1", Map.of("X-Request-Id", "id con espacios y <script>"));
        assertFalse(hostile.headers().firstValue("X-Request-Id").orElse("").contains("script"), "no se acepta un identificador con caracteres peligrosos");
    }

    @Test
    @DisplayName("las lecturas son públicas; las escrituras exigen X-Api-Key (401 sin ella o con una incorrecta)")
    void apiKey() throws Exception {
        assertEquals(200, get("/api/products/1").statusCode());
        clear(); // a partir de aquí se cuentan solo las peticiones que intentan escribir

        HttpResponse<String> without = call("POST", "/api/products", Map.of());
        assertEquals(401, without.statusCode());
        assertTrue(without.body().contains("\"status\":401") && without.body().contains("requestId"), without.body());
        assertEquals(401, call("POST", "/api/products", Map.of("X-Api-Key", "clave-incorrecta")).statusCode());
        assertEquals(0, productA.hits.get() + productB.hits.get(), "las peticiones rechazadas nunca llegan al servicio");

        assertEquals(201, call("POST", "/api/products", Map.of("X-Api-Key", "demo-key-123")).statusCode());
        assertEquals(201, call("POST", "/api/orders", Map.of("X-Api-Key", "otra-clave-456")).statusCode());
        assertEquals(401, call("DELETE", "/api/products/1", Map.of()).statusCode());
    }

    @Test
    @DisplayName("reintenta las lecturas fallidas en OTRA copia: con una copia defectuosa todos los GET salen bien")
    void retriesReadsOnAnotherInstance() throws Exception {
        for (int i = 0; i < 12; i++) {
            assertEquals(200, get("/api/products/flaky").statusCode(), "intento " + i);
        }
        assertTrue(productB.hits.get() > 0, "la copia defectuosa sí recibió peticiones");
        assertTrue(productB.received.size() > 0 && productA.hits.get() >= 12, "las 12 lecturas terminaron en la copia sana");
    }

    @Test
    @DisplayName("NO reintenta las escrituras (repetir un POST podría duplicar datos): el 503 llega al cliente")
    void doesNotRetryWrites() throws Exception {
        Set<Integer> statuses = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            statuses.add(call("POST", "/api/products/flaky", Map.of("X-Api-Key", "demo-key-123")).statusCode());
        }
        // la copia B devuelve 503 en /products/flaky para cualquier método (el 201 solo lo da la sana)
        assertEquals(Set.of(201, 503), statuses);
    }

    @Test
    @DisplayName("una ruta que no existe devuelve 404")
    void unknownRouteIsNotFound() throws Exception {
        assertEquals(404, get("/api/inventado/x").statusCode());
        assertEquals(404, get("/otra-cosa").statusCode());
    }

    @Test
    @DisplayName("una copia lenta que supera el tiempo máximo produce 504 Gateway Timeout")
    void timeout() throws Exception {
        for (int i = 0; i < 4; i++) {
            HttpResponse<String> response = get("/api/products/slow");
            assertEquals(504, response.statusCode());
        }
    }

    @Test
    @DisplayName("/gateway/services lista los servicios y sus copias descubiertos")
    void listsDiscoveredServices() throws Exception {
        HttpResponse<String> response = get("/gateway/services");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("product-service") && response.body().contains("order-service"), response.body());
        assertTrue(response.body().contains(String.valueOf(productA.port())) && response.body().contains(String.valueOf(productB.port())));
    }

    @Test
    @DisplayName("CORS: un origen permitido recibe las cabeceras; uno ajeno no")
    void cors() throws Exception {
        HttpResponse<String> allowed = call("OPTIONS", "/api/products", Map.of("Origin", "http://localhost:5173",
                "Access-Control-Request-Method", "GET"));
        assertEquals("http://localhost:5173", allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

        HttpResponse<String> foreign = call("OPTIONS", "/api/products", Map.of("Origin", "http://sitio-ajeno.example",
                "Access-Control-Request-Method", "GET"));
        assertNotNull(foreign);
        assertTrue(foreign.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }

    @Test
    @DisplayName("el actuator expone las rutas configuradas")
    void actuatorGatewayRoutes() throws Exception {
        HttpResponse<String> response = get("/actuator/gateway/routes");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("product-service") && response.body().contains("lb://order-service"), response.body());
    }
}
