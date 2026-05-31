package vn.t3nexus.customer.infrastructure.messaging.customer;

public record CustomerAccountCreatedPayload(
        String userId,
        String email,
        String fullName
) {}
