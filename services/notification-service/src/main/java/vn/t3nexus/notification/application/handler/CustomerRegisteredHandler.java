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
public class CustomerRegisteredHandler implements NotificationEventHandler<CustomerRegisteredPayload> {

    private final ULIDGenerator ulidGenerator;

    @Override
    public String supportedEventType() {
        return "CustomerRegisteredEvent";
    }

    @Override
    public Class<CustomerRegisteredPayload> payloadType() {
        return CustomerRegisteredPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, CustomerRegisteredPayload payload) {
        // OAUTH users are already verified by the provider — no verification email needed
        if (!"CREDENTIAL".equals(payload.registrationMethod())) {
            return NotificationResult.empty();
        }

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
                payload.aggregateId(),
                payload.email(),
                notificationPayload
        );

        return NotificationResult.emailOnly(log);
    }
}
