package vn.t3nexus.notification.application.handler;

public record PasswordSetupResentPayload(String userId, String email, String setupToken) {}
