package vn.t3nexus.inventory.presentation.stock.model;

import jakarta.validation.constraints.Min;

public record SetStockQuantityRequest(
        @Min(0) int quantity,
        @Min(0) Integer lowStockThreshold
) {}
