package vn.t3nexus.notification.application.handler;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceTrustOtpRequestedPayload(
        String email,
        String fullName,
        String otp
) {}
