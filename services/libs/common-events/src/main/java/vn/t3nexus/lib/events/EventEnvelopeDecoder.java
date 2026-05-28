package vn.t3nexus.lib.events;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Decode phần payload của EventEnvelope sang type cụ thể của từng BC.
 * Inject ObjectMapper từ Spring context của consuming service.
 *
 * Usage:
 * <pre>
 *   CustomerRegisteredPayload p = decoder.decode(envelope, CustomerRegisteredPayload.class);
 * </pre>
 */
public final class EventEnvelopeDecoder {

    private final ObjectMapper objectMapper;

    public EventEnvelopeDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T decode(OutboxEventData event, Class<T> payloadType) {
        return objectMapper.convertValue(event.payload().payload(), payloadType);
    }

    public <T> T decode(OutboxEventData event, TypeReference<T> payloadType) {
        return objectMapper.convertValue(event.payload().payload(), payloadType);
    }

}
