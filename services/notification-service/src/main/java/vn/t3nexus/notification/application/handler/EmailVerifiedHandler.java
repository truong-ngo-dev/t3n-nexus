package vn.t3nexus.notification.application.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.notification.application.notification.NotificationEventHandler;
import vn.t3nexus.notification.application.notification.NotificationResult;
import vn.t3nexus.notification.application.notification.NotificationTrigger;
import vn.t3nexus.notification.domain.notification.*;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailVerifiedHandler implements NotificationEventHandler<EmailVerifiedPayload> {

    private final ULIDGenerator ulidGenerator;

    @Override
    public String supportedEventType() {
        return "EmailVerifiedEvent";
    }

    @Override
    public Class<EmailVerifiedPayload> payloadType() {
        return EmailVerifiedPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, EmailVerifiedPayload payload) {
        NotificationPayload notificationPayload = NotificationPayload.of(
                "Tài khoản của bạn đã được kích hoạt",
                "Chào mừng bạn đến với T3Nexus. Tài khoản của bạn đã sẵn sàng.",
                Map.of(
                        "templateVars", Map.of(
                                "fullName", payload.fullName()
                        )
                )
        );

        NotificationLog log = NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()),
                trigger.eventId(),
                NotificationType.ACCOUNT_ACTIVATED,
                NotificationChannel.EMAIL,
                NotificationTier.TRANSACTIONAL,
                payload.userId(),
                payload.email(),
                notificationPayload
        );

        return NotificationResult.emailOnly(log);
    }
}
