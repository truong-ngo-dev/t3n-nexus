package vn.t3nexus.emailworker.application.email;

public enum EmailNotificationType {
    VERIFICATION_EMAIL("email/verification"),
    ACCOUNT_ACTIVATED("email/account-activated");

    private final String templatePath;

    EmailNotificationType(String templatePath) {
        this.templatePath = templatePath;
    }

    public String getTemplatePath() { return templatePath; }
}
