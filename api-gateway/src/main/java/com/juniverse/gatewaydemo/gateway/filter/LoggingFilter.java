package com.juniverse.gatewaydemo.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Registra cada petición: método, ruta, servicio destino, código de respuesta y duración. */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            LOG.info("{} {} -> {} [{}] {} ms (id {})", exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(),
                    route == null ? "sin ruta" : route.getId(), exchange.getResponse().getStatusCode() == null ? "?" : exchange.getResponse().getStatusCode().value(),
                    (System.nanoTime() - start) / 1_000_000, exchange.getResponse().getHeaders().getFirst(RequestIdFilter.HEADER));
        });
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
