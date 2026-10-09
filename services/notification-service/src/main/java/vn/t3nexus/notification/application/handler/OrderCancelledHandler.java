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
 * <b>Chỉ In-App</b> — cùng lý do hoãn Email đã ghi ở {@link OrderConfirmedHandler}. Chỉ báo buyer —
 * payload {@code OrderCancelledEvent} không mang {@code sellerId} (khác {@code OrderConfirmedEvent}).
 * {@code reason} giữ String thô (không parse enum `OrderCancelReason` của order-service) — cross-BC,
 * không nên phụ thuộc kiểu dữ liệu nội bộ của service khác.
 */
@Component
@RequiredArgsConstructor
public class OrderCancelledHandler implements NotificationEventHandler<OrderCancelledPayload> {

    private final ULIDGenerator ulidGenerator;

    @Override
    public String supportedEventType() {
        return "OrderCancelledEvent";
    }

    @Override
    public Class<OrderCancelledPayload> payloadType() {
        return OrderCancelledPayload.class;
    }

    @Override
    public NotificationResult handle(NotificationTrigger trigger, OrderCancelledPayload payload) {
        NotificationPayload content = NotificationPayload.of(
                "Đơn hàng đã bị huỷ",
                describeCancellation(payload)
        );

        NotificationLog log = NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()), trigger.eventId(),
                NotificationType.ORDER_CANCELLED, NotificationChannel.IN_APP, NotificationTier.TRANSACTIONAL,
                payload.customerId(), null, content);

        NotificationInbox inbox = NotificationInbox.create(
                NotificationInboxId.of(ulidGenerator.generate()), log.getId(),
                payload.customerId(), content.getTitle(), content.getBody(), null);

        return NotificationResult.of(List.of(log), List.of(inbox));
    }

    private String describeCancellation(OrderCancelledPayload payload) {
        return switch (payload.reason()) {
            case "OUT_OF_STOCK" -> "Đơn hàng #%s đã bị huỷ do sản phẩm đã hết hàng.".formatted(payload.orderId());
            case "INVENTORY_TIMEOUT" -> "Đơn hàng #%s đã bị huỷ do quá thời gian xử lý.".formatted(payload.orderId());
            default -> "Đơn hàng #%s đã bị huỷ.".formatted(payload.orderId());
        };
    }
}
