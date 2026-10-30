package com.juniverse.gatewaydemo.gateway.filter;

import com.juniverse.gatewaydemo.gateway.config.GatewayProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Autenticación por clave de API, aplicada en un solo lugar: las LECTURAS (GET) son públicas y las operaciones que MODIFICAN datos exigen
 * la cabecera {@code X-Api-Key}. Los servicios no necesitan saber nada de esto.
 *
 * <p>Ojo: esto protege el camino a través del gateway. Los servicios deben ser inalcanzables desde fuera (red privada); si alguien puede
 * llamarlos directamente, se salta el gateway y toda su seguridad.</p>
 */
@Component
public class ApiKeyFilter implements GlobalFilter, Ordered {

    private static final Set<HttpMethod> OPEN_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

    private final Set<byte[]> keys;

    /**
     * Crea el filtro.
     *
     * @param properties propiedades del gateway
     */
    public ApiKeyFilter(GatewayProperties properties) {
        this.keys = properties.apiKeys().stream().map(key -> key.getBytes(StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean protectedPath = exchange.getRequest().getURI().getPath().startsWith("/api/");
        if (!protectedPath || OPEN_METHODS.contains(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        String received = exchange.getRequest().getHeaders().getFirst("X-Api-Key");
        if (received == null || !isValid(received.getBytes(StandardCharsets.UTF_8))) {
            return JsonResponses.error(exchange, HttpStatus.UNAUTHORIZED, "Falta una clave de API válida en la cabecera X-Api-Key");
        }
        return chain.filter(exchange);
    }

    /** Compara contra todas las claves en tiempo constante, sin detenerse en la primera coincidencia. */
    private boolean isValid(byte[] candidate) {
        boolean valid = false;
        for (byte[] key : keys) {
            valid |= MessageDigest.isEqual(key, candidate);
        }
        return valid;
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
