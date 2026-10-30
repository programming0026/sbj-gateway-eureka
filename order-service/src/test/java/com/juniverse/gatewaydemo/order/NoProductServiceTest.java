package com.juniverse.gatewaydemo.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Qué pasa cuando NO hay ninguna copia de product-service registrada. */
@SpringBootTest(properties = {"eureka.client.enabled=false"})
@DisplayName("Servicio de pedidos sin product-service")
class NoProductServiceTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("responde 503 con un mensaje claro, no un error interno")
    void respondsServiceUnavailable() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable());
    }
}
