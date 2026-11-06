package vn.t3nexus.lib.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Khung của mọi event giữa các bounded context, là giá trị của thông điệp trên bus.
 * <br>Dựng bằng {@link EventEnvelopeFactory} (bên phát), đọc bằng {@link EventEnvelopeReader} (bên nhận).
 * <br>{@code traceId} nối log của hai bên; {@code spanId} (tùy chọn) để bên nhận đặt làm span cha khi nối cây trace.
 * <br>Tiến hoá chỉ thêm trường: bên nhận bỏ qua trường lạ; đổi nghĩa thì tăng {@code schemaVersion}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        String eventId,
        String aggregateId,
        String aggregateType,
        String eventType,
        Instant occurredAt,
        String traceId,
        String spanId,
        Integer schemaVersion,
        Object payload
) {}
