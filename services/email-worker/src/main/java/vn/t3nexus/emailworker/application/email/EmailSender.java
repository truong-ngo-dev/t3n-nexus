package vn.t3nexus.emailworker.application.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.base-url}")
    private String baseUrl;

    public void send(EmailDispatchEvent event) {
        try {
            MimeMessage message = buildMessage(event);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to build email message: notificationLogId=" + event.id() + ", eventId=" + event.eventId() + ", userId=" + event.userId(), e);
        }
    }

    private MimeMessage buildMessage(EmailDispatchEvent event) throws MessagingException {
        EmailPayload emailPayload = objectMapper.readValue(event.payload(), EmailPayload.class);

        Context ctx = new Context();
        ctx.setVariable("baseUrl", baseUrl);
        if (emailPayload.attributes() != null) {
            Object templateVars = emailPayload.attributes().get("templateVars");
            if (templateVars instanceof Map<?, ?> vars) {
                vars.forEach((k, v) -> ctx.setVariable(String.valueOf(k), v));
            }
        }

        String html = templateEngine.process(resolveTemplate(event.notificationType()), ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(event.recipient());
        helper.setSubject(emailPayload.title());
        helper.setText(html, true);
        return message;
    }

    private String resolveTemplate(String notificationType) {
        return EmailNotificationType.valueOf(notificationType).getTemplatePath();
    }
}
