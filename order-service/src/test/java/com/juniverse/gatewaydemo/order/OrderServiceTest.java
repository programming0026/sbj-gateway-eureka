package com.juniverse.gatewaydemo.order;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * El servicio de pedidos con DOS copias simuladas de product-service. El "directorio" de servicios es la configuración estática de Spring
 * Cloud ({@code SimpleDiscoveryClient}) en lugar de Eureka, pero el cliente con balanceo de carga es el mismo.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false"})
@DisplayName("Servicio de pedidos")
class OrderServiceTest {

    private static final List<HttpServer> STUBS = new ArrayList<>();
    private static final AtomicInteger REQUESTS_TO_A = new AtomicInteger();
    private static final AtomicInteger REQUESTS_TO_B = new AtomicInteger();

    /** Copia simulada de product-service: reserva correctamente salvo los productos 404 (no existe), 409 (sin stock) y 500 (falla). */
    private static HttpServer stub(String name, AtomicInteger counter) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/products/", exchange -> {
            counter.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            String id = path.split("/")[2];
            int status = switch (id) {
                case "404" -> 404;
                case "409" -> 409;
                case "500" -> 500;
                default -> 200;
            };
            String body = status == 200 ? "{\"productId\":" + id + ",\"name\":\"Producto " + id + "\",\"unitPrice\":150.50,\"quantity\":2}" : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("X-Served-By", "product-service:" + name);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        STUBS.add(server);
        return server;
    }

    @DynamicPropertySource
    static void productInstances(DynamicPropertyRegistry registry) throws IOException {
        HttpServer a = stub("A", REQUESTS_TO_A);
        HttpServer b = stub("B", REQUESTS_TO_B);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", () -> "http://localhost:" + a.getAddress().getPort());
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[1].uri", () -> "http://localhost:" + b.getAddress().getPort());
    }

    @AfterAll
    static void stopStubs() {
        STUBS.forEach(s -> s.stop(0));
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final String ORDER = "{\"productId\":%d,\"quantity\":2}";

    @Test
    @DisplayName("crea un pedido: reserva en product-service, calcula el total y lo guarda")
    void createsAnOrder() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER.formatted(7)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productName").value("Producto 7"))
                .andExpect(jsonPath("$.total").value(301.00))
                .andExpect(jsonPath("$.reservedBy").value(startsWith("product-service:")));
        mvc.perform(get("/orders")).andExpect(status().isOk()).andExpect(jsonPath("$[0].productId").value(7));
    }

    @Test
    @DisplayName("el cliente reparte las llamadas entre las dos copias de product-service (round robin)")
    void loadBalancesAcrossInstances() throws Exception {
        Set<String> servedBy = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String body = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER.formatted(1)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            servedBy.add(com.jayway.jsonpath.JsonPath.read(body, "$.reservedBy"));
        }
        assertTrue(servedBy.containsAll(Set.of("product-service:A", "product-service:B")), servedBy.toString());
        assertTrue(REQUESTS_TO_A.get() >= 3 && REQUESTS_TO_B.get() >= 3, "A=" + REQUESTS_TO_A + " B=" + REQUESTS_TO_B);
    }

    @Test
    @DisplayName("traduce los errores de product-service: producto inexistente -> 404, sin stock -> 409, falla -> 503")
    void translatesErrors() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER.formatted(404))).andExpect(status().isNotFound());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER.formatted(409))).andExpect(status().isConflict());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER.formatted(500))).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("REGRESIÓN: ningún RestClient.Builder del contexto tiene balanceo de carga (el cliente de Eureka usaría ese y el servicio no podría registrarse)")
    void noLoadBalancedBuilderIsExposed() {
        for (String name : context.getBeanNamesForType(org.springframework.web.client.RestClient.Builder.class)) {
            org.junit.jupiter.api.Assertions.assertNull(context.findAnnotationOnBean(name, org.springframework.cloud.client.loadbalancer.LoadBalanced.class),
                    "el bean " + name + " está marcado @LoadBalanced");
        }
    }

    @Test
    @DisplayName("valida la petición y devuelve 404 para un pedido inexistente")
    void validatesAndNotFound() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":0,\"quantity\":0}")).andExpect(status().isBadRequest());
        mvc.perform(get("/orders/99999")).andExpect(status().isNotFound());
    }
}
