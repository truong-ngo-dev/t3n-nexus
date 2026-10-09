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
 * Kênh Email cho {@code OrderConfirmedEvent} — cố tình KHÔNG đi qua {@code NotificationEventHandler}/
 * {@code NotificationHandlerRegistry} (chỉ hỗ trợ 1 handler/eventType, dùng cho pool Tier1 chung) vì
 * đây là consumer group RIÊNG ({@code OrderEmailOutboxEventConsumer}), tách khỏi Tier1 OTP/verification
 * để tránh priority-inversion (xem service.md §Channel Routing "Open question").
 *
 * <p><b>TODO — seller</b>: chỉ gửi cho customer. Chưa có nguồn lấy email seller (không nằm trong request
 * context lúc đặt hàng — khác customer, vốn capture được từ JWT lúc gọi API). Cần cơ chế resolve riêng
 * (tra cứu identity-service hoặc tương đương) trước khi implement nhánh seller.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendOrderConfirmedEmail {

    private final ULIDGenerator ulidGenerator;
    private final NotificationLogRepository logRepository;

    @Transactional
    public void handle(NotificationTrigger trigger, OrderConfirmedPayload payload) {
        NotificationPayload content = NotificationPayload.of(
                "Đơn hàng đã được xác nhận",
                "Đơn hàng #%s đã được xác nhận và đang được xử lý.".formatted(payload.orderId())
        );

        NotificationLog customerLog = NotificationLog.create(
                NotificationLogId.of(ulidGenerator.generate()), trigger.eventId(),
                NotificationType.ORDER_CONFIRMED, NotificationChannel.EMAIL, NotificationTier.TRANSACTIONAL,
                payload.customerId(), payload.customerEmail(), content);
        logRepository.save(customerLog);

        // TODO: gửi email cho seller (payload.sellerId()) — chưa có email seller, xem javadoc class này.

        log.info("[SendOrderConfirmedEmail] queued: orderId={}, customerId={}, traceId={}",
                payload.orderId(), payload.customerId(), trigger.eventId());
    }
}
