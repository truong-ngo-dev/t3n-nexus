package vn.t3nexus.notification.application.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.notification.application.notification.NotificationEventHandler;
import vn.t3nexus.notification.application.notification.NotificationResult;
import vn.t3nexus.notification.application.notification.NotificationTrigger;
import vn.t3nexus.notification.domain.notification.NotificationChannel;
import vn.t3nexus.notification.domain.notification.NotificationLog;
import vn.t3nexus.notification.domain.notification.NotificationLogId;
import vn.t3nexus.notification.domain.notification.NotificationPayload;
import vn.t3nexus.notification.domain.notification.NotificationTier;
import vn.t3nexus.notification.domain.notification.NotificationType;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class VerificationEmailRequestedHandler implements NotificationEventHandler<VerificationEmailRequestedPayload> {

    private final ULIDGenerator ulidGenerator;

    @Override
    public String supportedEventType() {
        return "VerificationEmailRequested";
    }

    @Override
    public Class<VerificationEmailRequestedPayload> payloadType() {
        return VerificationEmailRequestedPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, VerificationEmailRequestedPayload payload) {
        NotificationPayload notificationPayload = NotificationPayload.of(
                "Xác nhận email của bạn",
                "Nhấp vào link bên dưới để kích hoạt tài khoản.",
                Map.of(
                        "templateVars", Map.of(
                                "fullName",          payload.fullName(),
                                "verificationToken", payload.verificationToken()
                        )
                )
        );

        NotificationLog log = NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()),
                trigger.eventId(),
                NotificationType.VERIFICATION_EMAIL,
                NotificationChannel.EMAIL,
                NotificationTier.TRANSACTIONAL,
                trigger.aggregateId(),
                payload.email(),
                notificationPayload
        );

        return NotificationResult.emailOnly(log);
    }
}
