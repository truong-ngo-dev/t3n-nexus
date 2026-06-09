package vn.t3nexus.notification.application.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
public class PasswordSetupResentHandler implements NotificationEventHandler<PasswordSetupResentPayload> {

    private final ULIDGenerator ulidGenerator;

    @Value("${app.oauth2.url}")
    private String oauth2Url;

    @Override
    public String supportedEventType() {
        return "PasswordSetupResentEvent";
    }

    @Override
    public Class<PasswordSetupResentPayload> payloadType() {
        return PasswordSetupResentPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, PasswordSetupResentPayload payload) {
        String setupUrl = oauth2Url + "/password/setup?token=" + payload.setupToken();

        NotificationPayload notificationPayload = NotificationPayload.of(
                "Thiết lập mật khẩu cho tài khoản của bạn",
                "Đây là link thiết lập mật khẩu mới nhất. Link trước đó đã bị vô hiệu hóa.",
                Map.of(
                        "templateVars", Map.of(
                                "setupUrl", setupUrl
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
