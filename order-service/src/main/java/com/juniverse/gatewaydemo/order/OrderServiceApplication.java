package com.juniverse.gatewaydemo.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de pedidos. Se registra en Eureka como {@code ORDER-SERVICE} y, además de ser consumido por el gateway, él mismo
 * <b>consume</b> a {@code product-service} usando su NOMBRE (no una dirección): el cliente HTTP con balanceo de carga pregunta a Eureka
 * por las copias disponibles y reparte las llamadas entre ellas.
 */
@SpringBootApplication
public class OrderServiceApplication {

    /**
     * Arranca el servicio.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
