package vn.t3nexus.notification.application.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.notification.application.notification.NotificationEventHandler;
import vn.t3nexus.notification.application.notification.NotificationResult;
import vn.t3nexus.notification.application.notification.NotificationTrigger;
import vn.t3nexus.notification.domain.notification.NotificationChannel;
import vn.t3nexus.notification.domain.notification.NotificationInbox;
import vn.t3nexus.notification.domain.notification.NotificationInboxId;
import vn.t3nexus.notification.domain.notification.NotificationLog;
import vn.t3nexus.notification.domain.notification.NotificationLogId;
import vn.t3nexus.notification.domain.notification.NotificationPayload;
import vn.t3nexus.notification.domain.notification.NotificationTier;
import vn.t3nexus.notification.domain.notification.NotificationType;

import java.util.List;

/**
 * <b>Chỉ In-App</b> — Email cố tình hoãn (xem "Open question" ở {@code service.md} §Channel Routing):
 * volume tỉ lệ thuận order count có thể vượt trần SES 14 msg/s + gây priority inversion nếu share
 * pool với Tier1 OTP/verification hiện có; ngoài ra payload chỉ có {@code customerId}/{@code sellerId}
 * (ID), chưa có cơ chế resolve email — cả 2 vấn đề đều chưa quyết, không tự ý giải quyết ở đây.
 * In-App không đụng SES nên không vướng rủi ro đó, và không cần email address (chỉ cần userId).
 * Gửi cả buyer lẫn seller — đúng thiết kế "gửi thông báo buyer + seller" trong design.md.
 */
@Component
@RequiredArgsConstructor
public class OrderConfirmedHandler implements NotificationEventHandler<OrderConfirmedPayload> {

    private final ULIDGenerator ulidGenerator;

    @Override
    public String supportedEventType() {
        return "OrderConfirmedEvent";
    }

    @Override
    public Class<OrderConfirmedPayload> payloadType() {
        return OrderConfirmedPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, OrderConfirmedPayload payload) {
        NotificationPayload content = NotificationPayload.of(
                "Đơn hàng đã được xác nhận",
                "Đơn hàng #%s đã được xác nhận và đang được xử lý.".formatted(payload.orderId())
        );

        NotificationLog customerLog = createLog(trigger, payload.customerId(), content);
        NotificationLog sellerLog = createLog(trigger, payload.sellerId(), content);

        NotificationInbox customerInbox = createInbox(payload.customerId(), customerLog, content);
        NotificationInbox sellerInbox = createInbox(payload.sellerId(), sellerLog, content);

        return NotificationResult.of(
                List.of(customerLog, sellerLog),
                List.of(customerInbox, sellerInbox));
    }

    private NotificationLog createLog(NotificationTrigger trigger, String userId, NotificationPayload content) {
        return NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()), trigger.eventId(),
                NotificationType.ORDER_CONFIRMED, NotificationChannel.IN_APP, NotificationTier.TRANSACTIONAL,
                userId, null, content);
    }

    private NotificationInbox createInbox(String userId, NotificationLog log, NotificationPayload content) {
        return NotificationInbox.create(
                NotificationInboxId.of(ulidGenerator.generate()), log.getId(),
                userId, content.getTitle(), content.getBody(), null);
    }
}
