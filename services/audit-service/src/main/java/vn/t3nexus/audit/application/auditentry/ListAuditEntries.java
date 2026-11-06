package vn.t3nexus.audit.application.auditentry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditErrorCode;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Use case tra cứu danh sách bản ghi kiểm toán có bộ lọc và phân trang con trỏ.
 * <br>Bảo đảm không ghi nhật ký tra cứu và kiểm tra tham số nghiêm ngặt.
 */
@Service
public class ListAuditEntries implements QueryHandler<ListAuditEntries.Query, ListAuditEntries.Result> {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private final AuditEntryQuery auditEntryQuery;

    public ListAuditEntries(AuditEntryQuery auditEntryQuery) {
        this.auditEntryQuery = auditEntryQuery;
    }

    @Override
    @Transactional(readOnly = true)
    public Result handle(Query query) {
        int limit = query.limit() != null ? query.limit() : DEFAULT_LIMIT;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new DomainException(AuditErrorCode.AUDIT_LIMIT_INVALID);
        }

        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new DomainException(AuditErrorCode.AUDIT_TIME_RANGE_INVALID);
        }

        String action = stripToNull(query.action());
        String targetType = stripToNull(query.targetType());
        String targetId = stripToNull(query.targetId());
        String cursorStr = stripToNull(query.cursor());

        if (targetId != null && targetType == null) {
            throw new DomainException(AuditErrorCode.AUDIT_FILTER_INVALID);
        }

        AuditCursor cursor = null;
        if (cursorStr != null) {
            cursor = AuditCursor.decode(cursorStr);
        }

        AuditEntryFilter filter = new AuditEntryFilter(
                query.from(),
                query.to(),
                query.actorId(),
                action,
                targetType,
                targetId
        );

        List<AuditEntry> records = auditEntryQuery.search(filter, cursor, limit + 1);

        List<AuditEntry> items;
        String nextCursor;

        if (records.size() > limit) {
            items = records.subList(0, limit);
            nextCursor = AuditCursor.of(items.get(items.size() - 1)).encode();
        } else {
            items = records;
            nextCursor = null;
        }

        return new Result(items, nextCursor);
    }

    private static String stripToNull(String s) {
        if (s == null) {
            return null;
        }
        String stripped = s.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public record Query(
            Instant from,
            Instant to,
            UUID actorId,
            String action,
            String targetType,
            String targetId,
            Integer limit,
            String cursor
    ) {}

    public record Result(List<AuditEntry> items, String nextCursor) {}
}
