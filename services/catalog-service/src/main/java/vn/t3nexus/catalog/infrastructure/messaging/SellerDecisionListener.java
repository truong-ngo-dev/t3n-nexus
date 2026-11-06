package vn.t3nexus.catalog.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.application.sellerstate.RecordSellerDecision;
import vn.t3nexus.catalog.domain.product.SellerStatus;
import vn.t3nexus.lib.eventconsumer.EventEnvelopeConsumer;
import vn.t3nexus.lib.eventconsumer.NonRetryableEventException;

import java.util.Map;
import java.util.UUID;

/**
 * Nhận kết quả duyệt hồ sơ người bán từ {@code seller-service} (EVT-SEL-01, EVT-SEL-02) để giữ bản sao trạng thái.
 * Khung hỏng hoặc thiếu trường là lỗi không thử lại được: vào hàng chờ lỗi ngay (ADR-0010). Lỗi cơ sở dữ liệu thử lại
 * có backoff. Offset chỉ commit sau khi xử lý xong (ack sau commit).
 */
@Component
public class SellerDecisionListener {

    private final EventEnvelopeConsumer consumer;
    private final RecordSellerDecision record;

    public SellerDecisionListener(EventEnvelopeConsumer consumer, RecordSellerDecision record) {
        this.consumer = consumer;
        this.record = record;
    }

    @KafkaListener(topics = "${app.catalog.seller-approved-topic}")
    public void onApproved(String body) {
        handle(body, SellerStatus.APPROVED);
    }

    @KafkaListener(topics = "${app.catalog.seller-rejected-topic}")
    public void onRejected(String body) {
        handle(body, SellerStatus.REJECTED);
    }

    private void handle(String body, SellerStatus status) {
        consumer.consume(body, envelope -> {
            if (!(envelope.payload() instanceof Map<?, ?> payload)) {
                throw new NonRetryableEventException("Nội dung sự kiện duyệt người bán không phải đối tượng", null);
            }
            UUID accountId = uuid(payload.get("accountId"));
            long version = version(payload.get("version"));
            record.handle(new RecordSellerDecision.Command(accountId, status, version));
        });
    }

    private static UUID uuid(Object value) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            throw new NonRetryableEventException("accountId của sự kiện duyệt người bán không hợp lệ", e);
        }
    }

    private static long version(Object value) {
        if (value instanceof Number number && number.longValue() >= 0) {
            return number.longValue();
        }
        throw new NonRetryableEventException("version của sự kiện duyệt người bán không hợp lệ", null);
    }
}
