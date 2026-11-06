package vn.t3nexus.audit.application.auditentry;

import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Con trỏ phân trang dựa trên cặp (occurredAt, id) để duyệt dữ liệu ổn định không lặp hay sót.
 * <br>Chi tiết của phía đọc (cách API duyệt trang), không phải khái niệm nghiệp vụ của miền.
 */
public record AuditCursor(Instant occurredAt, UUID id) {

    public AuditCursor {
        Objects.requireNonNull(occurredAt, "occurredAt không được null");
        Objects.requireNonNull(id, "id không được null");
    }

    /**
     * Mã hóa con trỏ thành chuỗi Base64 URL-safe không đệm padding.
     */
    public String encode() {
        long epochSec = occurredAt.getEpochSecond();
        long nano = occurredAt.getNano();
        long micros = Math.addExact(Math.multiplyExact(epochSec, 1_000_000L), nano / 1_000L);
        String raw = micros + "." + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Giải mã chuỗi con trỏ thành AuditCursor. Bất kỳ lỗi định dạng nào cũng ném AUDIT_CURSOR_INVALID.
     */
    public static AuditCursor decode(String text) {
        if (text == null || text.isEmpty()) {
            throw new DomainException(AuditErrorCode.AUDIT_CURSOR_INVALID);
        }
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.getUrlDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new DomainException(AuditErrorCode.AUDIT_CURSOR_INVALID);
        }

        String raw = new String(decodedBytes, StandardCharsets.UTF_8);
        int dotIdx = raw.indexOf('.');
        if (dotIdx <= 0 || dotIdx == raw.length() - 1) {
            throw new DomainException(AuditErrorCode.AUDIT_CURSOR_INVALID);
        }

        String microsStr = raw.substring(0, dotIdx);
        String uuidStr = raw.substring(dotIdx + 1);

        long micros;
        try {
            micros = Long.parseLong(microsStr);
        } catch (NumberFormatException e) {
            throw new DomainException(AuditErrorCode.AUDIT_CURSOR_INVALID);
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            throw new DomainException(AuditErrorCode.AUDIT_CURSOR_INVALID);
        }

        long epochSec = Math.floorDiv(micros, 1_000_000L);
        long remMicros = Math.floorMod(micros, 1_000_000L);
        Instant instant = Instant.ofEpochSecond(epochSec, remMicros * 1_000L);

        return new AuditCursor(instant, uuid);
    }

    /**
     * Tạo con trỏ từ một bản ghi kiểm toán.
     */
    public static AuditCursor of(AuditEntry e) {
        Objects.requireNonNull(e, "Bản ghi kiểm toán không được null");
        return new AuditCursor(e.occurredAt(), e.id());
    }
}
