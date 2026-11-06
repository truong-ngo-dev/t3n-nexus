package vn.t3nexus.audit.domain.auditentry;

import vn.t3nexus.lib.common.domain.model.AbstractId;
import vn.t3nexus.lib.common.domain.model.Id;

import java.util.Objects;
import java.util.UUID;

/**
 * Mã bản ghi kiểm toán: kiểu mã riêng của thực thể, bọc một UUID v7 (ADR-0001).
 */
public class AuditEntryId extends AbstractId<UUID> implements Id<UUID> {

    private AuditEntryId(UUID value) {
        super(Objects.requireNonNull(value, "id"));
    }

    public static AuditEntryId of(UUID value) {
        return new AuditEntryId(value);
    }
}
