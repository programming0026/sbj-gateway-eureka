package com.juniverse.gatewaydemo.order;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Cliente HTTP con balanceo de carga del lado del cliente. */
@Configuration
public class HttpClientConfig {

    /**
     * Cliente hacia {@code product-service}. El interceptor de balanceo traduce el host de la URL ({@code product-service}) a una dirección real
     * consultando a Eureka, y cada llamada va a una copia distinta (round robin).
     *
     * <p><b>Trampa que se evita aquí:</b> lo habitual es declarar un bean {@code @LoadBalanced RestClient.Builder} y construir el cliente con él.
     * Pero el propio cliente de Eureka también usa el {@code RestClient.Builder} que encuentre en el contexto para hablar con el SERVIDOR Eureka;
     * si ese constructor tiene balanceo, intenta "descubrir" la dirección de {@code localhost} usando a Eureka, que aún no ha podido conectar: un
     * ciclo ({@code BeanCurrentlyInCreationException}) y el servicio nunca se registra. Por eso aquí el interceptor se añade a UN solo cliente y
     * no se expone ningún constructor con balanceo como bean.</p>
     *
     * @param loadBalancerClient balanceador de Spring Cloud
     * @return cliente apuntando al nombre lógico del servicio
     */
    @Bean
    RestClient productClient(LoadBalancerClient loadBalancerClient) {
        return RestClient.builder()
                .baseUrl("http://product-service")
                .requestInterceptor(new LoadBalancerInterceptor(loadBalancerClient, new LoadBalancerRequestFactory(loadBalancerClient)))
                .build();
    }
}
