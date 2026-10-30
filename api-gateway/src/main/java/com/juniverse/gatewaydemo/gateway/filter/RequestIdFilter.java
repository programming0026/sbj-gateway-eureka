package com.juniverse.gatewaydemo.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Identificador de correlación: cada petición lleva un {@code X-Request-Id}. Si el cliente ya envía uno se conserva; si no, se genera.
 * Viaja hacia los servicios (que pueden incluirlo en sus registros) y vuelve en la respuesta, así una petición se puede rastrear a través de
 * todos los componentes.
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    /** Nombre de la cabecera. */
    public static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER);
        // Un identificador enviado por el cliente se acepta solo si es corto y "seguro": no se debe poder inyectar contenido arbitrario en los registros
        String requestId = existing != null && existing.matches("[A-Za-z0-9._-]{1,64}") ? existing : UUID.randomUUID().toString();
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, requestId).build();
        exchange.getResponse().getHeaders().set(HEADER, requestId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -300;
    }
}
