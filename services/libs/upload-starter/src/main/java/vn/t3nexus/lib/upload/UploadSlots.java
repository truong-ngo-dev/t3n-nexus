package vn.t3nexus.lib.upload;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Xử lý vòng đời các chỗ tải lên (ADR-0013).
 * <br>Pha 1 (verify): gọi kho đối tượng để kiểm tra tính hợp lệ của tệp, KHÔNG được gọi trong giao dịch đang giữ khóa.
 * <br>Pha 2 (consume): chỉ thao tác cơ sở dữ liệu để tiêu thụ chỗ tải lên, yêu cầu chạy trong giao dịch.
 */
public class UploadSlots {

    private static final Logger LOGGER = System.getLogger(UploadSlots.class.getName());

    private final UploadProperties props;
    private final ObjectStore store;
    private final UploadSlotStore slots;
    private final IdGenerator ids;
    private final Clock clock;

    public UploadSlots(UploadProperties props, ObjectStore store, UploadSlotStore slots, IdGenerator ids, Clock clock) {
        this.props = props;
        this.store = store;
        this.slots = slots;
        this.ids = ids;
        this.clock = clock;
    }

    public record IssueRequest(String purpose, UUID accountId, UUID targetId, String contentType, long size) {
    }

    public record Issued(UUID slotId, String objectKey, String url, Map<String, String> fields, Instant expiresAt, long maxSize) {
    }

    public record Verified(UUID slotId, String purpose, String objectKey, String contentType, long size) {
    }

    public record Claimed(String objectKey, String contentType, long size) {
    }

    /**
     * Cấp chỗ tải lên mới và tạo form ký sẵn (presigned post) từ kho đối tượng.
     *
     * @param r yêu cầu cấp chỗ
     * @return thông tin chỗ tải lên kèm url và các trường form ký sẵn
     */
    public Issued issue(IssueRequest r) {
        UploadProperties.Purpose purpose = props.getPurposes().get(r.purpose());
        if (purpose == null) {
            throw new IllegalArgumentException("Mục đích không hợp lệ: " + r.purpose());
        }

        if (purpose.getAllowedTypes() == null || !purpose.getAllowedTypes().contains(r.contentType())) {
            throw new DomainException(UploadErrorCode.UPLOAD_TYPE_NOT_ALLOWED);
        }

        if (r.size() <= 0 || r.size() > purpose.getMaxSize()) {
            throw new DomainException(UploadErrorCode.UPLOAD_SIZE_INVALID);
        }

        Instant now = clock.instant();
        int activeCount = slots.countActive(r.purpose(), r.accountId(), r.targetId(), now);
        if (activeCount >= purpose.getMaxPending()) {
            throw new DomainException(UploadErrorCode.UPLOAD_PENDING_LIMIT);
        }

        UUID slotId = ids.generate();
        String objectKey = purpose.getKeyPrefix() + "/" + r.targetId() + "/" + slotId;
        Instant expiresAt = now.plus(purpose.getTtl());

        ObjectStore.PostForm postForm;
        try {
            postForm = store.presignPost(purpose.getBucket(), objectKey, r.contentType(), purpose.getMaxSize(), purpose.getTtl());
        } catch (ObjectStoreException e) {
            throw new DomainException(UploadErrorCode.UPLOAD_STORAGE_UNAVAILABLE);
        }

        UploadSlot slot = new UploadSlot(
                slotId,
                r.purpose(),
                r.accountId(),
                r.targetId(),
                objectKey,
                r.contentType(),
                r.size(),
                expiresAt,
                now
        );
        slots.save(slot);

        return new Issued(slotId, objectKey, postForm.url(), postForm.fields(), expiresAt, purpose.getMaxSize());
    }

    /**
     * Pha 1 (ADR-0013): Kiểm tra tệp đã được tải lên kho và thỏa mãn chữ ký, dung lượng.
     * <br>Hàm này gọi kho đối tượng, KHÔNG được gọi trong giao dịch đang giữ khóa.
     *
     * @param slotId    mã chỗ tải lên
     * @param purpose   mục đích tải lên
     * @param accountId mã tài khoản
     * @param targetId  mã đối tượng mục tiêu
     * @return đối tượng xác thực thành công
     */
    public Verified verify(UUID slotId, String purpose, UUID accountId, UUID targetId) {
        UploadProperties.Purpose purposeConfig = props.getPurposes().get(purpose);
        if (purposeConfig == null) {
            throw new DomainException(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND);
        }

        UploadSlot slot = slots.findById(slotId).orElse(null);
        if (slot == null
                || !slot.purpose().equals(purpose)
                || !slot.accountId().equals(accountId)
                || !slot.targetId().equals(targetId)) {
            throw new DomainException(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND);
        }

        Instant now = clock.instant();
        if (!slot.expiresAt().isAfter(now)) {
            throw new DomainException(UploadErrorCode.UPLOAD_SLOT_EXPIRED);
        }

        String bucket = purposeConfig.getBucket();
        String key = slot.objectKey();

        ObjectStore.StoredObject stat;
        try {
            stat = store.stat(bucket, key).orElse(null);
        } catch (ObjectStoreException e) {
            throw new DomainException(UploadErrorCode.UPLOAD_STORAGE_UNAVAILABLE);
        }

        if (stat == null) {
            throw new DomainException(UploadErrorCode.UPLOAD_NOT_COMPLETED);
        }

        long actualSize = stat.size();
        if (actualSize <= 0 || actualSize > purposeConfig.getMaxSize()) {
            discardQuietly(bucket, key);
            slots.deleteById(slotId);
            throw new DomainException(UploadErrorCode.UPLOAD_CONTENT_INVALID);
        }

        byte[] head;
        try {
            head = store.readHead(bucket, key, FileSignature.HEAD_LENGTH);
        } catch (ObjectStoreException e) {
            throw new DomainException(UploadErrorCode.UPLOAD_STORAGE_UNAVAILABLE);
        }

        var detected = FileSignature.detect(head);
        if (detected.isEmpty() || !detected.get().equals(slot.contentType())) {
            discardQuietly(bucket, key);
            slots.deleteById(slotId);
            throw new DomainException(UploadErrorCode.UPLOAD_CONTENT_INVALID);
        }

        return new Verified(slotId, purpose, key, slot.contentType(), actualSize);
    }

    /**
     * Pha 2 (ADR-0013): Tiêu thụ chỗ tải lên trong giao dịch cơ sở dữ liệu.
     * <br>Chỉ thao tác cơ sở dữ liệu (xóa dòng theo slotId) để đảm bảo không dùng lại chỗ tải lên.
     *
     * @param v kết quả đã xác thực từ pha 1
     * @return kết quả tiêu thụ
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Claimed consume(Verified v) {
        boolean deleted = slots.deleteById(v.slotId());
        if (!deleted) {
            throw new DomainException(UploadErrorCode.UPLOAD_SLOT_NOT_FOUND);
        }
        return new Claimed(v.objectKey(), v.contentType(), v.size());
    }

    /**
     * Hủy bỏ và xóa đối tượng trong kho (nuốt mọi RuntimeException).
     *
     * @param purpose   mục đích
     * @param objectKey khóa đối tượng
     */
    public void discard(String purpose, String objectKey) {
        UploadProperties.Purpose purposeConfig = props.getPurposes().get(purpose);
        if (purposeConfig == null) {
            LOGGER.log(Level.WARNING, "Không tìm thấy cấu hình mục đích để discard: " + purpose);
            return;
        }
        try {
            store.delete(purposeConfig.getBucket(), objectKey);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Không thể xóa đối tượng " + objectKey + " trong bucket " + purposeConfig.getBucket(), e);
        }
    }

    /**
     * Lấy đường dẫn URL công khai của đối tượng đã tải lên.
     *
     * @param purpose   mục đích
     * @param objectKey khóa đối tượng
     * @return URL công khai
     */
    public String urlOf(String purpose, String objectKey) {
        UploadProperties.Purpose purposeConfig = props.getPurposes().get(purpose);
        if (purposeConfig == null || !purposeConfig.isPublicRead()) {
            throw new IllegalStateException("Mục đích không hỗ trợ đọc công khai hoặc không tồn tại: " + purpose);
        }

        String baseUrl = purposeConfig.getPublicBaseUrl();
        if (baseUrl == null) {
            baseUrl = "";
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + "/" + purposeConfig.getBucket() + "/" + objectKey;
    }

    /**
     * Dọn dẹp các chỗ tải lên đã hết hạn quá thời gian ân hạn.
     *
     * @return số chỗ đã được dọn dẹp
     */
    public int cleanExpired() {
        Instant now = clock.instant();
        Instant before = now.minus(props.getExpiredGrace());
        List<UploadSlot> expiredSlots = slots.findExpired(before, props.getCleanBatch());

        int count = 0;
        for (UploadSlot slot : expiredSlots) {
            UploadProperties.Purpose purposeConfig = props.getPurposes().get(slot.purpose());
            if (purposeConfig != null) {
                discardQuietly(purposeConfig.getBucket(), slot.objectKey());
            }
            slots.deleteById(slot.id());
            count++;
        }

        return count;
    }

    private void discardQuietly(String bucket, String key) {
        try {
            store.delete(bucket, key);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Không thể xóa đối tượng " + key + " trong bucket " + bucket, e);
        }
    }
}
