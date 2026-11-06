package vn.t3nexus.catalog.application.product;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Chỗ tải lên ảnh sản phẩm đã cấp kèm đường dẫn và các trường form ký sẵn.
 */
public record UploadSlotView(UUID slotId, String url, Map<String, String> fields, Instant expiresAt, long maxSize) {
}
