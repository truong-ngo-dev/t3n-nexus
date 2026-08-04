package vn.t3nexus.order.presentation.order.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import vn.t3nexus.order.domain.order.PaymentMethod;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank String sellerId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        @NotNull PaymentMethod paymentMethod,
        @NotNull @Valid ShippingAddressRequest address
) {}
