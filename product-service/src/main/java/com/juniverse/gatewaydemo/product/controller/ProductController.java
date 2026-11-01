package com.juniverse.gatewaydemo.product.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.juniverse.gatewaydemo.product.model.Product;
import com.juniverse.gatewaydemo.product.repository.ProductStore;

/**
 * API de productos. NO sabe nada del gateway ni de Eureka: expone sus rutas ({@code /products}) como cualquier API. El gateway se encarga
 * del prefijo público {@code /api}.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    /**
     * Datos para crear un producto.
     *
     * @param name nombre
     * @param price precio
     * @param stock existencias
     */
    public record CreateProductRequest(@NotBlank(message = "El nombre es obligatorio") String name,
            @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero") BigDecimal price,
            @Min(value = 0, message = "Las existencias no pueden ser negativas") int stock) {
    }

    /**
     * Petición de reserva de existencias.
     *
     * @param quantity unidades a reservar
     */
    public record ReserveRequest(@Min(value = 1, message = "La cantidad mínima es 1") int quantity) {
    }

    /**
     * Reserva confirmada: lo que necesita quien hace el pedido.
     *
     * @param productId producto
     * @param name nombre
     * @param unitPrice precio unitario
     * @param quantity unidades reservadas
     */
    public record Reservation(long productId, String name, BigDecimal unitPrice, int quantity) {
    }

    private final ProductStore store;

    /**
     * Crea el controlador.
     *
     * @param store catálogo
     */
    public ProductController(ProductStore store) {
        this.store = store;
    }

    /**
     * Lista los productos.
     *
     * @return catálogo
     */
    @GetMapping
    public List<Product> list() {
        return store.findAll();
    }

    /**
     * Obtiene un producto.
     *
     * @param id identificador
     * @return el producto
     */
    @GetMapping("/{id}")
    public Product get(@PathVariable long id) {
        return store.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el producto " + id));
    }

    /**
     * Crea un producto.
     *
     * @param request datos validados
     * @return 201 con el producto y su ubicación
     */
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProductRequest request) {
        Product product = store.add(request.name().trim(), request.price(), request.stock());
        return ResponseEntity.created(URI.create("/products/" + product.id())).body(product);
    }

    /**
     * Reserva existencias (lo usa el servicio de pedidos).
     *
     * @param id producto
     * @param request cantidad
     * @return datos de la reserva
     */
    @PostMapping("/{id}/reserve")
    public Reservation reserve(@PathVariable long id, @Valid @RequestBody ReserveRequest request) {
        return switch (store.reserve(id, request.quantity())) {
            case RESERVED -> {
                Product product = store.find(id).orElseThrow();
                yield new Reservation(id, product.name(), product.price(), request.quantity());
            }
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el producto " + id);
            case INSUFFICIENT_STOCK -> throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay existencias suficientes del producto " + id);
        };
    }
}
