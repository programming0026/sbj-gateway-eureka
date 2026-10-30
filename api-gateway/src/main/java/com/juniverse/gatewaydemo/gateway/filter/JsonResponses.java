package com.juniverse.gatewaydemo.gateway.filter;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Respuestas de error en JSON que escribe el propio gateway (sin llegar a ningún servicio). */
final class JsonResponses {

    private JsonResponses() {
    }

    /**
     * Responde con un error en JSON y termina la petición.
     *
     * @param exchange petición actual
     * @param status código HTTP
     * @param message descripción para el cliente
     * @return señal de finalización
     */
    static Mono<Void> error(ServerWebExchange exchange, HttpStatus status, String message) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.HEADER);
        String json = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + message.replace("\"", "'")
                + "\",\"requestId\":\"" + (requestId == null ? "" : requestId) + "\"}";
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
