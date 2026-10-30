package com.juniverse.gatewaydemo.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** El servidor Eureka real, con su protocolo REST: un servicio se registra, aparece en el registro y se da de baja. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Servidor Eureka")
class DiscoveryServerTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> call(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("el panel de control responde")
    void dashboardIsServed() throws Exception {
        HttpResponse<String> response = call("GET", "/", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Eureka"));
    }

    @Test
    @DisplayName("un servicio se registra, aparece en el registro y se da de baja")
    void registerListAndDeregister() throws Exception {
        String instance = """
                {"instance":{"instanceId":"demo-1","hostName":"localhost","app":"DEMO-APP","ipAddr":"127.0.0.1","status":"UP",
                 "port":{"$":9999,"@enabled":"true"},"dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
                """;
        assertEquals(204, call("POST", "/eureka/apps/DEMO-APP", instance).statusCode());

        HttpResponse<String> apps = call("GET", "/eureka/apps/DEMO-APP", null);
        assertEquals(200, apps.statusCode());
        assertTrue(apps.body().contains("demo-1") && apps.body().contains("9999"), apps.body());

        assertEquals(200, call("PUT", "/eureka/apps/DEMO-APP/demo-1", null).statusCode(), "latido (renovación del contrato)");
        assertEquals(200, call("DELETE", "/eureka/apps/DEMO-APP/demo-1", null).statusCode());
        // La consulta de UNA instancia va directa al registro y refleja la baja al instante. La lista de aplicaciones (/eureka/apps) sale de una
        // caché de solo lectura que se refresca cada pocos segundos (eureka.server.response-cache-update-interval-ms): Eureka es eventualmente
        // consistente, y por eso los clientes tardan unos segundos en enterarse de que una copia se fue.
        assertEquals(404, call("GET", "/eureka/apps/DEMO-APP/demo-1", null).statusCode());
        org.awaitility.Awaitility.await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                .until(() -> call("GET", "/eureka/apps/DEMO-APP", null).statusCode() == 404);
    }
}
