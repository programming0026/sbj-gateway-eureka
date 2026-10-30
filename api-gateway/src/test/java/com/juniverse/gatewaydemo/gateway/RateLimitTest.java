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

/** Límite de peticiones por cliente con un umbral bajo (5 por minuto). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"eureka.client.enabled=false", "gateway.rate-limit-per-minute=5"})
@DisplayName("Límite de peticiones")
class RateLimitTest {

    private static StubService product;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) throws Exception {
        product = new StubService("product-A", false);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", product::uri);
    }

    @AfterAll
    static void stop() {
        product.close();
    }

    @LocalServerPort
    private int port;

    private HttpResponse<String> get(String apiKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/products/1"));
        if (apiKey != null) {
            builder.header("X-Api-Key", apiKey);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("la petición 6 recibe 429 con Retry-After; otro cliente (otra clave) tiene su propio contador")
    void limitsPerClient() throws Exception {
        for (int i = 1; i <= 5; i++) {
            HttpResponse<String> ok = get("cliente-A");
            assertEquals(200, ok.statusCode());
            assertEquals(String.valueOf(5 - i), ok.headers().firstValue("X-RateLimit-Remaining").orElse(null));
        }
        HttpResponse<String> limited = get("cliente-A");
        assertEquals(429, limited.statusCode());
        assertTrue(Integer.parseInt(limited.headers().firstValue("Retry-After").orElse("0")) >= 1);
        assertTrue(limited.body().contains("Demasiadas peticiones"));

        assertEquals(200, get("cliente-B").statusCode(), "otro cliente no se ve afectado");
        assertEquals(6, product.hits.get(), "al servicio solo llegaron las peticiones permitidas: 5 del cliente A y 1 del B; la 429 se frenó en el gateway");
    }
}
