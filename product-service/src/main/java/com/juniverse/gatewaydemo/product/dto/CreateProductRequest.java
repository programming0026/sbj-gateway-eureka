package com.juniverse.gatewaydemo.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

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
