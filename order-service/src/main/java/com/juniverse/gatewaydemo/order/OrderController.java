package com.juniverse.gatewaydemo.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API de pedidos (rutas internas {@code /orders}; el gateway las publica como {@code /api/orders}). */
@RestController
@RequestMapping("/orders")
public class OrderController {

    /**
     * Datos de un pedido.
     *
     * @param productId producto
     * @param quantity unidades
     */
    public record CreateOrderRequest(@Min(value = 1, message = "El producto es obligatorio") long productId,
            @Min(value = 1, message = "La cantidad mínima es 1") int quantity) {
    }

    private final OrderService service;

    /**
     * Crea el controlador.
     *
     * @param service servicio de pedidos
     */
    public OrderController(OrderService service) {
        this.service = service;
    }

    /**
     * Crea un pedido.
     *
     * @param request datos validados
     * @return 201 con el pedido
     */
    @PostMapping
    public ResponseEntity<OrderService.Order> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderService.Order order = service.create(request.productId(), request.quantity());
        return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
    }

    /**
     * Lista los pedidos.
     *
     * @return pedidos
     */
    @GetMapping
    public List<OrderService.Order> list() {
        return service.findAll();
    }

    /**
     * Obtiene un pedido.
     *
     * @param id identificador
     * @return el pedido
     */
    @GetMapping("/{id}")
    public OrderService.Order get(@PathVariable long id) {
        return service.get(id);
    }
}
