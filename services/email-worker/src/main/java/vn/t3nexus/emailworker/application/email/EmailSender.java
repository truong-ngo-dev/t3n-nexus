package vn.t3nexus.emailworker.application.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @Value("${app.mail.from}")
    private String mailFrom;

    // Mọi link trong email đều trỏ vào Angular SPA (không trỏ thẳng backend API nữa — trước đây
    // verification.html trỏ apiBaseUrl trả JSON trần, giờ đổi sang /verify-email FE route tự gọi
    // API và hiện màn hình kết quả, xem docs/feature/01-customer-registration).
    @Value("${app.frontend.url}")
    private String frontendBaseUrl;

    public void send(EmailDispatchEvent event) {
        try {
            MimeMessage message = buildMessage(event);
            mailSender.send(message);
            // Trước đây không có log nào xác nhận email thực sự rời SMTP — chỉ suy luận được qua
            // việc KHÔNG có exception, không phải bằng chứng trực tiếp.
            log.info("[EmailSender] sent: notificationLogId={}, eventId={}, userId={}, recipient={}",
                    event.id(), event.eventId(), event.userId(), event.recipient());
        } catch (Exception e) {
            // catch Exception (không chỉ MessagingException) — mailSender.send() ném MailException
            // (unchecked, khác nhánh với MessagingException lúc buildMessage()), trước đây không bị
            // bắt ở đây nên lỗi SMTP thật sự propagate mất hết context (notificationLogId/eventId)
            // thay vì được wrap như lỗi buildMessage. Log tường minh ở đây, không dựa vào log mặc
            // định của Spring Kafka error handler (context/format không do mình kiểm soát).
            log.error("[EmailSender] failed: notificationLogId={}, eventId={}, userId={}", event.id(), event.eventId(), event.userId(), e);
            throw new RuntimeException("Failed to send email: notificationLogId=" + event.id() + ", eventId=" + event.eventId() + ", userId=" + event.userId(), e);
        }
    }

    private MimeMessage buildMessage(EmailDispatchEvent event) throws MessagingException {
        EmailPayload emailPayload = objectMapper.readValue(event.payload(), EmailPayload.class);

        Context ctx = new Context();
        ctx.setVariable("frontendBaseUrl", frontendBaseUrl);
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
