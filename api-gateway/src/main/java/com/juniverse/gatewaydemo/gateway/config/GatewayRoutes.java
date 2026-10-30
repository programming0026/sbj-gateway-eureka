package com.juniverse.gatewaydemo.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * Las rutas del gateway. Una ruta = <b>predicados</b> (cuándo aplica) + <b>filtros</b> (qué se le hace a la petición) + <b>destino</b>.
 *
 * <p>El destino {@code lb://product-service} significa: "busca en el registro (Eureka) las copias de PRODUCT-SERVICE y reparte entre ellas".
 * Nadie escribe aquí una dirección IP ni un puerto.</p>
 */
@Configuration
public class GatewayRoutes {

    /**
     * Rutas públicas hacia los dos microservicios.
     *
     * @param builder constructor de rutas
     * @param properties propiedades del gateway
     * @return las rutas
     */
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, GatewayProperties properties) {
        return builder.routes()
                // GET /api/products/1  ->  product-service  GET /products/1
                .route("product-service", r -> r.path("/api/products/**")
                        .filters(f -> f
                                .rewritePath("/api/(?<segment>.*)", "/${segment}")
                                // Solo las lecturas se reintentan: repetir un POST podría crear dos pedidos. Cada reintento va a otra copia.
                                .retry(c -> c.setRetries(properties.retries()).setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE))
                                .addResponseHeader("X-Gateway-Route", "product-service"))
                        .uri("lb://product-service"))
                // POST /api/orders  ->  order-service  POST /orders
                .route("order-service", r -> r.path("/api/orders/**")
                        .filters(f -> f
                                .rewritePath("/api/(?<segment>.*)", "/${segment}")
                                .retry(c -> c.setRetries(properties.retries()).setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE))
                                .addResponseHeader("X-Gateway-Route", "order-service"))
                        .uri("lb://order-service"))
                .build();
    }
}
