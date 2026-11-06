package vn.t3nexus.audit.infrastructure.persistence;

import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditTarget;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

/**
 * Ánh xạ một dòng audit_entries thành {@link AuditEntry}; dùng chung cho kho (đọc theo mã) và truy vấn (tìm kiếm).
 */
final class AuditEntryRowMapper implements RowMapper<AuditEntry> {

    static final String SELECT_COLUMNS =
            "id, source_event_id, occurred_at, recorded_at, actor_account_id, actor_role, action, target_type, target_id, details::text AS details, source_service, trace_id";

    private final ObjectMapper objectMapper;

    AuditEntryRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String sourceEventId = rs.getString("source_event_id");
        Timestamp occurredAtTs = rs.getTimestamp("occurred_at");
        Timestamp recordedAtTs = rs.getTimestamp("recorded_at");
        UUID actorAccountId = rs.getObject("actor_account_id", UUID.class);
        String actorRole = rs.getString("actor_role");
        String action = rs.getString("action");
        String targetType = rs.getString("target_type");
        String targetId = rs.getString("target_id");
        String detailsRaw = rs.getString("details");
        String sourceService = rs.getString("source_service");
        String traceId = rs.getString("trace_id");

        Map<String, Object> details;
        try {
            if (detailsRaw != null && !detailsRaw.isBlank()) {
                details = objectMapper.readValue(detailsRaw, new TypeReference<Map<String, Object>>() {});
            } else {
                details = Map.of();
            }
        } catch (JacksonException e) {
            throw new IllegalStateException("Lỗi giải mã JSON details từ cơ sở dữ liệu", e);
        }

        return AuditEntry.restore(
                id,
                sourceEventId,
                occurredAtTs.toInstant(),
                recordedAtTs.toInstant(),
                new AuditActor(actorAccountId, actorRole),
                action,
                new AuditTarget(targetType, targetId),
                details,
                sourceService,
                traceId
        );
    }
}
