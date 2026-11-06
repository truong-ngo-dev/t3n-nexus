package vn.t3nexus.lib.events;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

/**
 * Dựng khung event cho bên phát. Kiểm trường bắt buộc và tên trường nhạy cảm ở mọi độ sâu của nội dung; vi phạm
 * là lỗi lập trình ({@link IllegalArgumentException}), không phải lỗi của người dùng.
 */
public class EventEnvelopeFactory {

    private static final String PAYLOAD = "payload";

    private final ObjectMapper objectMapper;
    private final SensitiveFieldPolicy policy;

    public EventEnvelopeFactory(ObjectMapper objectMapper, SensitiveFieldPolicy policy) {
        this.objectMapper = objectMapper;
        this.policy = policy;
    }

    public EventEnvelope create(String eventId, String aggregateType, String aggregateId, String eventType,
                                Instant occurredAt, String traceId, String spanId, int schemaVersion,
                                Object payload) {
        requireText("eventId", eventId);
        requireText("aggregateType", aggregateType);
        requireText("aggregateId", aggregateId);
        requireText("eventType", eventType);
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt là bắt buộc");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion phải từ 1 trở lên");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload là bắt buộc");
        }
        rejectSensitiveFields(objectMapper.valueToTree(payload), PAYLOAD);
        return new EventEnvelope(eventId, aggregateId, aggregateType, eventType, occurredAt, traceId,
                spanId, schemaVersion, payload);
    }

    public String toJson(EventEnvelope envelope) {
        return objectMapper.writeValueAsString(envelope);
    }

    private void rejectSensitiveFields(JsonNode node, String path) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : node.properties()) {
                String childPath = path + "." + property.getKey();
                if (policy.isForbidden(property.getKey())) {
                    throw new SensitiveFieldException(childPath);
                }
                rejectSensitiveFields(property.getValue(), childPath);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                rejectSensitiveFields(node.get(i), path + "[" + i + "]");
            }
        }
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " là bắt buộc");
        }
    }
}
