package vn.t3nexus.lib.events;

import org.slf4j.MDC;

/**
 * Inject các trường từ EventEnvelope vào MDC khi bắt đầu xử lý Kafka message,
 * để log tự động có traceId / correlationId / eventType mà không cần thêm code trong mỗi consumer.
 *
 * Usage:
 * <pre>
 *   EventEnvelopeMdcPropagator.propagate(envelope);
 *   try {
 *       // xử lý event
 *   } finally {
 *       EventEnvelopeMdcPropagator.clear();
 *   }
 * </pre>
 */
public final class EventEnvelopeMdcPropagator {

    private EventEnvelopeMdcPropagator() {}

    public static void propagate(EventEnvelope envelope) {
        if (envelope.traceId() != null) MDC.put("traceId", envelope.traceId());
        if (envelope.spanId() != null)  MDC.put("spanId",  envelope.spanId());
        MDC.put("eventId",   envelope.eventId());
        MDC.put("eventType", envelope.eventType());
    }

    public static void clear() {
        MDC.remove("traceId");
        MDC.remove("spanId");
        MDC.remove("eventId");
        MDC.remove("eventType");
    }
}
