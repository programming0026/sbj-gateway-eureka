package com.juniverse.gatewaydemo.gateway.filter;

import com.juniverse.gatewaydemo.gateway.config.GatewayProperties;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Límite de peticiones por cliente (ventana fija de un minuto). El cliente se identifica por su clave de API o, si no la envía, por su
 * dirección IP. Responde 429 con {@code Retry-After}.
 *
 * <p>Esta versión guarda los contadores en la memoria de UNA instancia del gateway. Con varias instancias hay que compartirlos (Redis, con el
 * filtro {@code RequestRateLimiter} de Spring Cloud Gateway).</p>
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final long WINDOW_MILLIS = 60_000;

    private record Window(long start, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    /**
     * Crea el filtro.
     *
     * @param properties propiedades del gateway
     * @param clock reloj
     */
    public RateLimitFilter(GatewayProperties properties, Clock clock) {
        this.limit = properties.rateLimitPerMinute();
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!exchange.getRequest().getURI().getPath().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        long now = clock.millis();
        String client = identify(exchange);
        Window window = windows.compute(client, (key, current) -> current == null || now - current.start() >= WINDOW_MILLIS
                ? new Window(now, new AtomicInteger()) : current);
        int used = window.count().incrementAndGet();
        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(limit));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - used)));
        if (used > limit) {
            long retryAfter = Math.max(1, (window.start() + WINDOW_MILLIS - now + 999) / 1000);
            exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(retryAfter));
            return JsonResponses.error(exchange, HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones; reintenta en " + retryAfter + " s");
        }
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS); // evita que el mapa crezca sin límite
        }
        return chain.filter(exchange);
    }

    private static String identify(ServerWebExchange exchange) {
        String key = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        if (key != null && !key.isBlank()) {
            return "key:" + key;
        }
        var address = exchange.getRequest().getRemoteAddress();
        return "ip:" + (address == null ? "desconocida" : address.getAddress().getHostAddress());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
