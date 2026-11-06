package vn.t3nexus.lib.upload;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cổng lưu chỗ đã cấp. Cài đặt thật dùng JPA trên bảng {@code upload_slot} của dịch vụ dùng thư viện. */
public interface UploadSlotStore {

    void save(UploadSlot slot);

    Optional<UploadSlot> findById(UUID id);

    /** Số chỗ chưa hết hạn ({@code expiresAt > now}) của cặp người và đối tượng theo mục đích. */
    int countActive(String purpose, UUID accountId, UUID targetId, Instant now);

    /** @return true nếu có một dòng bị xóa; false nếu đã bị ai xóa trước (dùng để chặn dùng một chỗ hai lần). */
    boolean deleteById(UUID id);

    /** Các chỗ có {@code expiresAt < before}, tối đa {@code limit} dòng, cũ trước. */
    List<UploadSlot> findExpired(Instant before, int limit);
}
