package com.juniverse.gatewaydemo.product.dto;

import jakarta.validation.constraints.Min;

/**
 * Petición de reserva de existencias.
 *
 * @param quantity unidades a reservar
 */
public record ReserveRequest(@Min(value = 1, message = "La cantidad mínima es 1") int quantity) {
}
