package com.juniverse.gatewaydemo.order.dto;

import java.math.BigDecimal;

/**
 * Respuesta de product-service al reservar unidades de un producto.
 *
 * @param productId producto reservado
 * @param name nombre del producto
 * @param unitPrice precio unitario en el momento de la reserva
 * @param quantity unidades reservadas
 */
public record Reservation(long productId, String name, BigDecimal unitPrice, int quantity) {
}
