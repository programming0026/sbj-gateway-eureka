package com.juniverse.gatewaydemo.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Qué ve el cliente cuando el servicio al que apunta la ruta no tiene ninguna copia registrada. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false"})
@DisplayName("Gateway sin copias de un servicio")
class NoInstancesTest {

    private static StubService product;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) throws Exception {
        product = new StubService("product-A", false);   // solo product-service: order-service NO tiene copias
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", product::uri);
    }

    @AfterAll
    static void stop() {
        product.close();
    }

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("responde 503 (el servicio no está disponible), con el identificador de la petición, y el resto de rutas siguen funcionando")
    void respondsServiceUnavailable() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> down = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders/1")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(503, down.statusCode());
        assertTrue(down.headers().firstValue("X-Request-Id").isPresent());

        HttpResponse<String> up = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/products/1")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, up.statusCode(), "la caída de un servicio no afecta a las rutas de los demás");
    }
}
