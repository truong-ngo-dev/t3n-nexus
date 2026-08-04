package vn.t3nexus.order.presentation.order.model;

import jakarta.validation.constraints.NotBlank;

public record ShippingAddressRequest(
        @NotBlank String recipientName,
        @NotBlank String phone,
        @NotBlank String addressLine,
        @NotBlank String ward,
        @NotBlank String province,
        String note
) {}
