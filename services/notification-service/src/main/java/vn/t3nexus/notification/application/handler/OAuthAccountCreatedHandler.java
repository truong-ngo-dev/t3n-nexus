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
public class OAuthAccountCreatedHandler implements NotificationEventHandler<PasswordSetupEmailRequestedPayload> {

    private final ULIDGenerator ulidGenerator;

    @Override
    public String supportedEventType() {
        return "PasswordSetupEmailRequested";
    }

    @Override
    public Class<PasswordSetupEmailRequestedPayload> payloadType() {
        return PasswordSetupEmailRequestedPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, PasswordSetupEmailRequestedPayload payload) {
        NotificationPayload notificationPayload = NotificationPayload.of(
                "Thiết lập mật khẩu cho tài khoản của bạn",
                "Tài khoản vừa được tạo qua đăng nhập mạng xã hội. Hãy đặt mật khẩu để bảo mật tài khoản.",
                Map.of(
                        "templateVars", Map.of(
                                "fullName", payload.fullName()
                        )
                )
        );

        NotificationLog log = NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()),
                trigger.eventId(),
                NotificationType.OAUTH_ACCOUNT_CREATED,
                NotificationChannel.EMAIL,
                NotificationTier.TRANSACTIONAL,
                trigger.aggregateId(),
                payload.email(),
                notificationPayload
        );

        return NotificationResult.emailOnly(log);
    }
}
