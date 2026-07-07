package vn.t3nexus.inventory.presentation.limitedoffer.model;

import jakarta.validation.constraints.Min;

public record ActivateLimitedOfferRequest(
        @Min(1) int maxQuantity,
        @Min(1) int windowSeconds
) {}
