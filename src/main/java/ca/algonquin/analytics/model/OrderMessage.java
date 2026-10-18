package ca.algonquin.analytics.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Matches the existing Node.js order-service message, without type headers. */
public record OrderMessage(
        @NotNull @Valid Product product,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.0") BigDecimal totalPrice) {
    public record Product(
            @NotNull @Positive Long id,
            @NotBlank String name,
            @NotNull @DecimalMin("0.0") BigDecimal price) { }
}
