package com.juniverse.gatewaydemo.product;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** API de productos (sin Eureka: se prueba el servicio por sí solo). */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.discovery.enabled=false"})
@DisplayName("Servicio de productos")
class ProductControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ProductStore store;
    @Autowired
    private ServedByFilter servedByFilter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(servedByFilter).build(); // MockMvc no añade los filtros solo
    }

    @Test
    @DisplayName("lista el catálogo y marca quién respondió (X-Served-By)")
    void listsProducts() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Laptop 14 pulgadas"))
                .andExpect(header().string("X-Served-By", org.hamcrest.Matchers.startsWith("product-service@")));
    }

    @Test
    @DisplayName("obtiene un producto o 404")
    void getsOne() throws Exception {
        mvc.perform(get("/products/2")).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(349.90));
        mvc.perform(get("/products/999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("crea un producto (201) y valida los datos (400)")
    void createsAndValidates() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Webcam\",\"price\":799.00,\"stock\":5}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.id").isNumber());
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"price\":0,\"stock\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("reserva existencias: 200, 404 si no existe, 409 si no alcanzan, 400 si la cantidad es inválida")
    void reserves() throws Exception {
        mvc.perform(post("/products/2/reserve").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unitPrice").value(349.90)).andExpect(jsonPath("$.quantity").value(3));
        mvc.perform(post("/products/999/reserve").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isNotFound());
        mvc.perform(post("/products/1/reserve").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1000}")).andExpect(status().isConflict());
        mvc.perform(post("/products/1/reserve").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("50 reservas simultáneas de 1 unidad sobre un stock de 20: exactamente 20 tienen éxito y el stock termina en 0")
    void concurrentReservationsNeverOversell() throws Exception {
        long id = store.add("Edición limitada", new java.math.BigDecimal("10.00"), 20).id();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> {
                if (store.reserve(id, 1) == ProductStore.ReserveResult.RESERVED) {
                    ok.incrementAndGet();
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        org.junit.jupiter.api.Assertions.assertEquals(20, ok.get());
        org.junit.jupiter.api.Assertions.assertEquals(0, store.find(id).orElseThrow().stock());
    }
}
