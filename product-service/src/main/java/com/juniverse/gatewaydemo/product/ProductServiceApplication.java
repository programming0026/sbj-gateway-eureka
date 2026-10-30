package com.juniverse.gatewaydemo.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de productos. Al arrancar se registra en Eureka como {@code PRODUCT-SERVICE}; basta ejecutar varias copias (con distinto
 * puerto) para que el gateway reparta las peticiones entre ellas.
 */
@SpringBootApplication
public class ProductServiceApplication {

    /**
     * Arranca el servicio.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
