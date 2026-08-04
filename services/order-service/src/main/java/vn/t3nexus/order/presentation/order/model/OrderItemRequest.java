package vn.t3nexus.order.presentation.order.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderItemRequest(
        @NotBlank String skuId,
        @Positive int qty,
        @PositiveOrZero long unitPrice
) {}
