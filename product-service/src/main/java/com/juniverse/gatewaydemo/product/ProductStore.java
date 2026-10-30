package com.juniverse.gatewaydemo.product;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Catálogo en memoria con tres productos de ejemplo (suficiente para el ejemplo; en un servicio real sería una base de datos). */
@Component
public class ProductStore {

    /** Resultado de intentar reservar existencias. */
    public enum ReserveResult {
        /** Se reservó. */
        RESERVED,
        /** No existe el producto. */
        NOT_FOUND,
        /** No hay existencias suficientes. */
        INSUFFICIENT_STOCK
    }

    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** Carga los productos de ejemplo. */
    public ProductStore() {
        add("Laptop 14 pulgadas", new BigDecimal("15999.00"), 10);
        add("Mouse inalámbrico", new BigDecimal("349.90"), 100);
        add("Teclado mecánico", new BigDecimal("1299.00"), 40);
    }

    /**
     * Agrega un producto.
     *
     * @param name nombre
     * @param price precio
     * @param stock existencias
     * @return el producto creado
     */
    public Product add(String name, BigDecimal price, int stock) {
        Product product = new Product(sequence.incrementAndGet(), name, price, stock);
        products.put(product.id(), product);
        return product;
    }

    /**
     * Todos los productos.
     *
     * @return productos ordenados por id
     */
    public List<Product> findAll() {
        return products.values().stream().sorted(Comparator.comparingLong(Product::id)).toList();
    }

    /**
     * Busca un producto.
     *
     * @param id identificador
     * @return el producto, si existe
     */
    public Optional<Product> find(long id) {
        return Optional.ofNullable(products.get(id));
    }

    /**
     * Descuenta existencias de forma atómica: la comprobación y la resta ocurren juntas, así dos pedidos simultáneos no pueden llevar el stock
     * a negativo.
     *
     * @param id producto
     * @param quantity unidades
     * @return resultado de la reserva
     */
    public ReserveResult reserve(long id, int quantity) {
        ReserveResult[] result = {ReserveResult.NOT_FOUND};
        products.computeIfPresent(id, (key, product) -> {
            if (product.stock() < quantity) {
                result[0] = ReserveResult.INSUFFICIENT_STOCK;
                return product;
            }
            result[0] = ReserveResult.RESERVED;
            return new Product(product.id(), product.name(), product.price(), product.stock() - quantity);
        });
        return result[0];
    }
}
