package vn.t3nexus.audit.application.auditentry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntryId;
import vn.t3nexus.audit.domain.auditentry.AuditEntryRepository;
import vn.t3nexus.audit.domain.auditentry.AuditErrorCode;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.Objects;
import java.util.UUID;

/**
 * Use case xem chi tiết một bản ghi kiểm toán theo mã định danh.
 * <br>Bảo đảm không ghi dữ liệu chi tiết vào nhật ký tra cứu.
 */
@Service
public class GetAuditEntry implements QueryHandler<GetAuditEntry.Query, GetAuditEntry.Result> {

    private final AuditEntryRepository auditEntryRepository;

    public GetAuditEntry(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        Objects.requireNonNull(query, "query không được null");
        AuditEntry entry = auditEntryRepository.findById(AuditEntryId.of(query.id()))
                .orElseThrow(() -> new DomainException(AuditErrorCode.AUDIT_ENTRY_NOT_FOUND));
        return new Result(entry);
    }

    public record Query(UUID id) {}

    public record Result(AuditEntry entry) {}
}
