package vn.t3nexus.audit.infrastructure.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.audit.application.auditentry.AuditCursor;
import vn.t3nexus.audit.application.auditentry.AuditEntryFilter;
import vn.t3nexus.audit.application.auditentry.AuditEntryQuery;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Cài cổng đọc {@link AuditEntryQuery} bằng NamedParameterJdbcTemplate: chỉ SELECT, câu SQL động dùng chỉ mục.
 */
@Repository
public class AuditEntryQueryAdapter implements AuditEntryQuery {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RowMapper<AuditEntry> rowMapper;

    public AuditEntryQueryAdapter(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = new AuditEntryRowMapper(objectMapper);
    }

    @Override
    public List<AuditEntry> search(AuditEntryFilter filter, AuditCursor after, int limit) {
        StringBuilder sql = new StringBuilder("SELECT ").append(AuditEntryRowMapper.SELECT_COLUMNS).append(" FROM audit_entries");
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filter != null) {
            if (filter.from() != null) {
                conditions.add("occurred_at >= :from");
                params.addValue("from", OffsetDateTime.ofInstant(filter.from(), ZoneOffset.UTC));
            }
            if (filter.to() != null) {
                conditions.add("occurred_at < :to");
                params.addValue("to", OffsetDateTime.ofInstant(filter.to(), ZoneOffset.UTC));
            }
            if (filter.actorId() != null) {
                conditions.add("actor_account_id = :actorId");
                params.addValue("actorId", filter.actorId());
            }
            if (filter.action() != null) {
                conditions.add("action = :action");
                params.addValue("action", filter.action());
            }
            if (filter.targetType() != null) {
                conditions.add("target_type = :targetType");
                params.addValue("targetType", filter.targetType());
            }
            if (filter.targetId() != null) {
                conditions.add("target_id = :targetId");
                params.addValue("targetId", filter.targetId());
            }
        }

        if (after != null) {
            conditions.add("(occurred_at, id) < (:cursorAt, :cursorId)");
            params.addValue("cursorAt", OffsetDateTime.ofInstant(after.occurredAt(), ZoneOffset.UTC));
            params.addValue("cursorId", after.id());
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit");
        params.addValue("limit", limit);

        return jdbcTemplate.query(sql.toString(), params, rowMapper);
    }
}
