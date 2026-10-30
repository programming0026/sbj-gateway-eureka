package com.juniverse.gatewaydemo.product;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Añade a cada respuesta la cabecera {@code X-Served-By} con el servicio, el equipo y el puerto que la atendieron. Es solo una ayuda didáctica:
 * con dos copias del servicio permite VER cómo el gateway reparte las peticiones.
 */
@Component
public class ServedByFilter extends OncePerRequestFilter {

    private final String applicationName;
    private final String host = hostName();

    /**
     * Crea el filtro.
     *
     * @param applicationName nombre del servicio
     */
    public ServedByFilter(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Served-By", applicationName + "@" + host + ":" + request.getLocalPort());
        chain.doFilter(request, response);
    }

    /** Nombre del equipo; en Docker es el identificador del contenedor, que distingue entre sí a las réplicas del mismo servicio. */
    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return "desconocido";
        }
    }
}
