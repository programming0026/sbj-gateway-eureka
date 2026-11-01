package com.juniverse.gatewaydemo.product.model;

import java.math.BigDecimal;

/**
 * Producto del catálogo.
 *
 * @param id identificador
 * @param name nombre
 * @param price precio unitario
 * @param stock existencias
 */
public record Product(long id, String name, BigDecimal price, int stock) {
}
