package com.juniverse.gatewaydemo.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Servidor de descubrimiento <b>Eureka</b>: la "agenda telefónica" de los microservicios. Cada servicio se registra al arrancar con su
 * nombre y su dirección y se da de baja al apagarse; quien necesite llamar a otro pregunta aquí dónde está, en lugar de tener su dirección
 * escrita en la configuración. Así se pueden levantar varias copias de un servicio, cambiarlas de lugar o reemplazarlas sin tocar a los
 * demás.
 *
 * <p>Panel de control: <a href="http://localhost:8761">http://localhost:8761</a>.</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    /**
     * Arranca el servidor.
     *
     * @param args argumentos de la línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
