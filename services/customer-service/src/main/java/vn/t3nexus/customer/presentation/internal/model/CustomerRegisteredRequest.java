package vn.t3nexus.customer.presentation.internal.model;

import jakarta.validation.constraints.NotBlank;

public record CustomerRegisteredRequest(
        @NotBlank String userId,
        @NotBlank String email,
        @NotBlank String fullName,
        @NotBlank String role,
                  String verificationToken
) {}
