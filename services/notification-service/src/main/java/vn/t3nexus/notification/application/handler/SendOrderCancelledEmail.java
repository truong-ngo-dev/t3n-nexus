package vn.t3nexus.notification.application.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;
import vn.t3nexus.notification.application.notification.NotificationTrigger;
import vn.t3nexus.notification.domain.notification.NotificationChannel;
import vn.t3nexus.notification.domain.notification.NotificationLog;
import vn.t3nexus.notification.domain.notification.NotificationLogId;
import vn.t3nexus.notification.domain.notification.NotificationLogRepository;
import vn.t3nexus.notification.domain.notification.NotificationPayload;
import vn.t3nexus.notification.domain.notification.NotificationTier;
import vn.t3nexus.notification.domain.notification.NotificationType;

/**
 * Kênh Email cho {@code OrderCancelledEvent} — cùng lý do tách riêng consumer group đã ghi ở
 * {@link SendOrderConfirmedEmail}. `OrderCancelledEvent` vốn không mang `sellerId`, nên không có
 * TODO seller ở đây (khác {@code OrderConfirmed}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendOrderCancelledEmail {

    private final ULIDGenerator ulidGenerator;
    private final NotificationLogRepository logRepository;

    @Transactional
    public void handle(NotificationTrigger trigger, OrderCancelledPayload payload) {
        NotificationPayload content = NotificationPayload.of(
                "Đơn hàng đã bị huỷ",
                describeCancellation(payload)
        );

        NotificationLog customerLog = NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()), trigger.eventId(),
                NotificationType.ORDER_CANCELLED, NotificationChannel.EMAIL, NotificationTier.TRANSACTIONAL,
                payload.customerId(), payload.customerEmail(), content);
        logRepository.save(customerLog);

        log.info("[SendOrderCancelledEmail] queued: orderId={}, customerId={}, traceId={}",
                payload.orderId(), payload.customerId(), trigger.eventId());
    }

    private String describeCancellation(OrderCancelledPayload payload) {
        return switch (payload.reason()) {
            case "OUT_OF_STOCK" -> "Đơn hàng #%s đã bị huỷ do sản phẩm đã hết hàng.".formatted(payload.orderId());
            case "INVENTORY_TIMEOUT" -> "Đơn hàng #%s đã bị huỷ do quá thời gian xử lý.".formatted(payload.orderId());
            default -> "Đơn hàng #%s đã bị huỷ.".formatted(payload.orderId());
        };
    }
}
