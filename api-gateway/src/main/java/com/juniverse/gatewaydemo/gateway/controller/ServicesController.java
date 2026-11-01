package com.juniverse.gatewaydemo.gateway.controller;

import java.util.List;
import java.util.Map;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** Muestra lo que el gateway "ve" en el registro de servicios: útil para comprobar qué copias hay disponibles. */
@RestController
@RequestMapping("/gateway")
public class ServicesController {

    private final ReactiveDiscoveryClient discovery;

    /**
     * Crea el controlador.
     *
     * @param discovery cliente de descubrimiento (Eureka)
     */
    public ServicesController(ReactiveDiscoveryClient discovery) {
        this.discovery = discovery;
    }

    /**
     * Servicios registrados y sus copias.
     *
     * @return mapa nombre de servicio -> direcciones de sus copias
     */
    @GetMapping("/services")
    public Mono<Map<String, List<String>>> services() {
        return discovery.getServices().flatMap(name -> discovery.getInstances(name).map(instance -> instance.getUri().toString()).collectList()
                .map(uris -> Map.entry(name, uris))).collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
}
