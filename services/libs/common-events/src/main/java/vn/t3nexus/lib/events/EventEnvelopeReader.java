package vn.t3nexus.lib.events;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Đọc khung event cho bên nhận. Bỏ qua trường lạ (tiến hoá chỉ thêm trường); khung không đọc được hoặc thiếu trường
 * bắt buộc thì ném {@link InvalidEventEnvelopeException} (thử lại không giúp gì).
 */
public class EventEnvelopeReader {

    private final ObjectMapper objectMapper;

    public EventEnvelopeReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope read(String body) {
        JsonNode tree;
        try {
            tree = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new InvalidEventEnvelopeException("Thông điệp không phải JSON hợp lệ");
        }
        if (tree == null || !tree.isObject()) {
            throw new InvalidEventEnvelopeException("Thông điệp không phải một đối tượng JSON");
        }

        EventEnvelope envelope;
        try {
            envelope = objectMapper.treeToValue(tree, EventEnvelope.class);
        } catch (JacksonException e) {
            throw new InvalidEventEnvelopeException("Khung event sai kiểu dữ liệu ở một trường");
        }

        requireText("eventId", envelope.eventId());
        requireText("eventType", envelope.eventType());
        requireText("aggregateType", envelope.aggregateType());
        requireText("aggregateId", envelope.aggregateId());
        if (envelope.occurredAt() == null) {
            throw missing("occurredAt");
        }
        if (envelope.schemaVersion() == null || envelope.schemaVersion() < 1) {
            throw missing("schemaVersion");
        }
        if (envelope.payload() == null) {
            throw missing("payload");
        }
        return envelope;
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw missing(name);
        }
    }

    private static InvalidEventEnvelopeException missing(String name) {
        return new InvalidEventEnvelopeException("Khung event thiếu hoặc sai trường bắt buộc: " + name);
    }
}
