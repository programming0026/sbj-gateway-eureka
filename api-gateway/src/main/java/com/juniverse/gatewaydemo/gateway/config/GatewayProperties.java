package com.juniverse.gatewaydemo.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code gateway.*}.
 *
 * @param apiKeys claves de API válidas para las operaciones que modifican datos (POST, PUT, PATCH y DELETE)
 * @param rateLimitPerMinute peticiones por minuto permitidas a cada cliente en las rutas {@code /api/**}
 * @param allowedOrigins orígenes (patrones) autorizados a llamar desde un navegador (CORS)
 * @param retries reintentos de las peticiones GET cuando una copia responde 502 o 503
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(List<String> apiKeys, int rateLimitPerMinute, List<String> allowedOrigins, int retries) {
}
