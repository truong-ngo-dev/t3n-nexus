package vn.t3nexus.notification.application.handler;

public record PasswordSetupEmailRequestedPayload(String userId, String email, String fullName, String setupToken) {}
