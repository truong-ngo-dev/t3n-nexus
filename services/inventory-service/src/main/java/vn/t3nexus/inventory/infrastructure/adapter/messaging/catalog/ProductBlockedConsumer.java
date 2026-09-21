package vn.t3nexus.inventory.infrastructure.adapter.messaging.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.inventory.application.stock.BlockProductStocks;
import vn.t3nexus.lib.events.EventEnvelopeDecoder;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.OutboxEventData;

/**
 * Idempotency: DB-based, không dùng Redis — {@code BlockProductStocks.handle()} set field tuyệt đối
 * trên toàn bộ Stock của product, naturally idempotent, bảo vệ bởi {@code @Version} từng row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductBlockedConsumer {

    private final ObjectMapper objectMapper;
    private final EventEnvelopeDecoder decoder;
    private final BlockProductStocks blockProductStocks;

    @KafkaListener(
            topics  = "${app.kafka.topic.product-blocked}",
            groupId = "${app.kafka.consumer-group.catalog}"
    )
    public void consume(String message) {
        OutboxEventData event = objectMapper.readValue(message, OutboxEventData.class);
        EventEnvelopeMdcPropagator.propagate(event.payload());

        try {
            Payload payload = decoder.decode(event, Payload.class);
            blockProductStocks.handle(new BlockProductStocks.Command(payload.productId()));
        } catch (Exception e) {
            log.error("[ProductBlockedConsumer] failed to process eventId={}", event.payload().eventId(), e);
            throw e;
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }

    private record Payload(String productId, String sellerId, String reason) {}
}
