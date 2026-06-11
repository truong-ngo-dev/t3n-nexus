package vn.t3nexus.emailworker.application.email;

public enum EmailNotificationType {
    LOGIN_OTP("email/login-otp"),
    DEVICE_TRUST_OTP("email/device-trust-otp"),
    VERIFICATION_EMAIL("email/verification"),
    ACCOUNT_ACTIVATED("email/account-activated"),
    OAUTH_ACCOUNT_CREATED("email/oauth-account-created");

    private final String templatePath;

    EmailNotificationType(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getTemplatePath() { return templatePath; }
}
