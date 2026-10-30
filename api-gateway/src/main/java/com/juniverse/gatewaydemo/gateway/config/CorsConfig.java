package com.juniverse.gatewaydemo.gateway.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/** CORS en un solo lugar: los servicios no necesitan configurarlo porque el navegador solo habla con el gateway. */
@Configuration
public class CorsConfig {

    /**
     * Filtro CORS para las rutas {@code /api/**}.
     *
     * @param properties propiedades del gateway
     * @return filtro
     */
    @Bean
    CorsWebFilter corsWebFilter(GatewayProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-Api-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-Served-By", "X-RateLimit-Remaining"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsWebFilter(source);
    }
}
