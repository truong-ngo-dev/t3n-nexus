package vn.t3nexus.notification.application.handler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledPayload(
        String orderId,
        String customerId,
        String customerEmail,
        String reason
) {}
