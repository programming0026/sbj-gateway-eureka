package com.juniverse.gatewaydemo.gateway;

import com.juniverse.gatewaydemo.gateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * <b>API Gateway</b>: la única puerta de entrada pública. Los clientes (navegador, app móvil) solo conocen {@code http://localhost:8080};
 * el gateway decide a qué microservicio enviar cada petición.
 *
 * <p>Qué aporta frente a que los clientes llamen directamente a cada servicio:</p>
 * <ul>
 *   <li><b>Una sola dirección</b>: los servicios pueden cambiar de lugar, escalar o reemplazarse sin que el cliente se entere.</li>
 *   <li><b>Descubrimiento y balanceo</b>: la ruta {@code lb://product-service} se resuelve con Eureka y reparte entre las copias.</li>
 *   <li><b>Preocupaciones transversales en un solo sitio</b>: identificador de petición, registro, autenticación por clave de API, límite de
 *       peticiones, CORS y reintentos; ninguno de los servicios tiene que implementarlos.</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(GatewayProperties.class)
public class ApiGatewayApplication {

    /**
     * Arranca el gateway.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
