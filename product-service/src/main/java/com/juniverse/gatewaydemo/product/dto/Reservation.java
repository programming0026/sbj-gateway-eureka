package com.juniverse.gatewaydemo.product.dto;

import java.math.BigDecimal;

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
