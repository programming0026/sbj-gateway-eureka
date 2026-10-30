package com.juniverse.gatewaydemo.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
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
 * Una de las dos copias registradas YA NO EXISTE (el puerto está cerrado) pero el balanceador aún la tiene en su lista, como pasa durante los
 * segundos que tarda Eureka en expulsarla. Ningún pedido debe fallar por eso.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false"})
@DisplayName("Servicio de pedidos con una copia muerta")
class DeadInstanceTest {

    private static final AtomicInteger HITS = new AtomicInteger();
    private static HttpServer alive;

    @DynamicPropertySource
    static void instances(DynamicPropertyRegistry registry) throws Exception {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();           // se reserva un puerto libre y se cierra: nadie escucha en él
        }
        alive = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        alive.createContext("/products/", exchange -> {
            HITS.incrementAndGet();
            byte[] body = "{\"productId\":1,\"name\":\"Vivo\",\"unitPrice\":10.00,\"quantity\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        alive.start();
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[0].uri", () -> "http://localhost:" + deadPort);
        registry.add("spring.cloud.discovery.client.simple.instances.product-service[1].uri", () -> "http://localhost:" + alive.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        alive.stop(0);
    }

    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("20 pedidos seguidos: todos salen bien, los que caen en la copia muerta se reintentan en la viva")
    void ordersSucceedDespiteTheDeadInstance() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (int i = 0; i < 20; i++) {
            mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.productName").value("Vivo"));
        }
        assertEquals(20, HITS.get(), "cada pedido reservó exactamente una vez en la copia viva (ningún reintento duplicó una reserva)");
    }
}
