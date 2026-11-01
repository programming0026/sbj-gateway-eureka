package com.juniverse.gatewaydemo.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Crea pedidos reservando las existencias en {@code product-service}. */
@Service
public class OrderService {

    /**
     * Pedido.
     *
     * @param id identificador
     * @param productId producto
     * @param productName nombre del producto al momento del pedido
     * @param quantity unidades
     * @param total importe (precio unitario × cantidad)
     * @param createdAt momento
     * @param reservedBy copia de product-service que atendió la reserva (para ver el balanceo de carga)
     */
    public record Order(long id, long productId, String productName, int quantity, BigDecimal total, Instant createdAt, String reservedBy) {
    }

    private record Reservation(long productId, String name, BigDecimal unitPrice, int quantity) {
    }

    private static final int CONNECT_ATTEMPTS = 3;

    private final RestClient products;
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Crea el servicio.
     *
     * @param productClient cliente (con balanceo de carga) hacia product-service
     */
    public OrderService(RestClient productClient) {
        this.products = productClient;
    }

    /**
     * Crea un pedido: reserva las existencias en product-service y guarda el pedido con el precio recibido.
     *
     * @param productId producto
     * @param quantity unidades
     * @return el pedido creado
     * @throws ResponseStatusException 404 si el producto no existe, 409 si no hay existencias, 503 si product-service no está disponible
     */
    public Order create(long productId, int quantity) {
        ResponseEntity<Reservation> response;
        try {
            response = reserve(productId, quantity);
        } catch (HttpClientErrorException e) {
            HttpStatusCode code = e.getStatusCode();
            if (code.value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el producto " + productId);
            }
            if (code.value() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay existencias suficientes del producto " + productId);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Respuesta inesperada de product-service: " + code.value());
        } catch (HttpServerErrorException | ResourceAccessException | IllegalStateException e) {
            // Sin copias disponibles en Eureka, conexión rechazada o error 5xx: el servicio del que dependemos no puede atender
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de productos no está disponible; inténtalo de nuevo en unos segundos");
        }
        Reservation reservation = response.getBody();
        Order order = new Order(sequence.incrementAndGet(), productId, reservation.name(), quantity,
                reservation.unitPrice().multiply(BigDecimal.valueOf(quantity)), Instant.now(), response.getHeaders().getFirst("X-Served-By"));
        orders.put(order.id(), order);
        return order;
    }

    /**
     * Llama a product-service. Si la conexión es RECHAZADA (la copia ya no existe pero el balanceador aún la tiene en su caché durante unos
     * segundos) se reintenta, y como el balanceador reparte en round robin el reintento va a OTRA copia. Es seguro reintentar porque con una
     * conexión rechazada la petición nunca llegó al servidor, así que no pudo reservar nada. Un tiempo de espera agotado, en cambio, NO se
     * reintenta: ahí no se sabe si el servidor llegó a reservar y se podrían descontar existencias dos veces.
     */
    private ResponseEntity<Reservation> reserve(long productId, int quantity) {
        ResourceAccessException lastFailure = null;
        for (int attempt = 1; attempt <= CONNECT_ATTEMPTS; attempt++) {
            try {
                return products.post().uri("/products/{id}/reserve", productId).body(Map.of("quantity", quantity)).retrieve().toEntity(Reservation.class);
            } catch (ResourceAccessException e) {
                if (!(e.getCause() instanceof java.net.ConnectException)) {
                    throw e;
                }
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    /**
     * Lista los pedidos.
     *
     * @return pedidos, del más reciente al más antiguo
     */
    public List<Order> findAll() {
        return orders.values().stream().sorted(Comparator.comparingLong(Order::id).reversed()).toList();
    }

    /**
     * Busca un pedido.
     *
     * @param id identificador
     * @return el pedido
     * @throws NoSuchElementException si no existe
     */
    public Order get(long id) {
        Order order = orders.get(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el pedido " + id);
        }
        return order;
    }
}
