package vn.t3nexus.lib.eventconsumer;

import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.lib.events.EventEnvelopeMdcPropagator;
import vn.t3nexus.lib.events.EventEnvelopeReader;

import java.util.function.Consumer;

/**
 * Cầu nối giữa listener Kafka của BC và handler của BC: đọc khung từ chuỗi nhận được, đưa {@code traceId},
 * {@code spanId}, {@code eventId}, {@code eventType} vào ngữ cảnh log trong lúc handler chạy, rồi xóa.
 * <br>Khung hỏng ném {@link vn.t3nexus.lib.events.InvalidEventEnvelopeException} trước khi gọi handler (bộ xử lý lỗi
 * chuyển thẳng sang hàng chờ lỗi). Lỗi của handler được ném lại nguyên vẹn.
 * <br>Handler tự chạy giao dịch của nó trọn vẹn bên trong lời gọi này: offset chỉ được commit sau khi
 * {@code consume} trả về (ack sau commit).
 */
public class EventEnvelopeConsumer {

    private final EventEnvelopeReader reader;

    public EventEnvelopeConsumer(EventEnvelopeReader reader) {
        this.reader = reader;
    }

    public void consume(String body, Consumer<EventEnvelope> handler) {
        EventEnvelope envelope = reader.read(body);
        EventEnvelopeMdcPropagator.propagate(envelope);
        try {
            handler.accept(envelope);
        } finally {
            EventEnvelopeMdcPropagator.clear();
        }
    }
}
