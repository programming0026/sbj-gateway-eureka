package com.juniverse.gatewaydemo.order.dto;

import jakarta.validation.constraints.Min;

/**
 * Datos de un pedido.
 *
 * @param productId producto
 * @param quantity unidades
 */
public record CreateOrderRequest(@Min(value = 1, message = "El producto es obligatorio") long productId,
        @Min(value = 1, message = "La cantidad mínima es 1") int quantity) {
}
