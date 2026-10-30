package com.juniverse.gatewaydemo.gateway.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Reloj inyectable. */
@Configuration
public class ClockConfig {

    /**
     * Reloj del sistema.
     *
     * @return reloj
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
