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
public class DeviceTrustOtpRequestedHandler implements NotificationEventHandler<DeviceTrustOtpRequestedPayload> {

    private final ULIDGenerator ulidGenerator;

    @Override
    public String supportedEventType() {
        return "DeviceOtpRequested";
    }

    @Override
    public Class<DeviceTrustOtpRequestedPayload> payloadType() {
        return DeviceTrustOtpRequestedPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, DeviceTrustOtpRequestedPayload payload) {
        NotificationPayload notificationPayload = NotificationPayload.of(
                "Xác nhận tin tưởng thiết bị",
                "Sử dụng mã OTP bên dưới để xác nhận tin tưởng thiết bị.",
                Map.of("templateVars", Map.of(
                        "otp",      payload.otp(),
                        "fullName", payload.fullName()
                ))
        );

        NotificationLog log = NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()),
                trigger.eventId(),
                NotificationType.DEVICE_TRUST_OTP,
                NotificationChannel.EMAIL,
                NotificationTier.TRANSACTIONAL,
                trigger.aggregateId(),
                payload.email(),
                notificationPayload
        );

        return NotificationResult.emailOnly(log);
    }
}
