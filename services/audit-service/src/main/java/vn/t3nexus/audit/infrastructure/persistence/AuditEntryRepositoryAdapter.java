package vn.t3nexus.audit.infrastructure.persistence;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.audit.domain.auditentry.AuditEntry;
import vn.t3nexus.audit.domain.auditentry.AuditEntryId;
import vn.t3nexus.audit.domain.auditentry.AuditEntryRepository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Cài cổng ghi {@link AuditEntryRepository} bằng NamedParameterJdbcTemplate: chỉ INSERT, bỏ qua khi trùng mã sự kiện nguồn.
 * Đọc theo mã (nạp bản ghi) ở đây; tìm kiếm có bộ lọc ở {@code AuditEntryQueryAdapter}.
 */
@Repository
public class AuditEntryRepositoryAdapter implements AuditEntryRepository {

    private static final String INSERT_SQL = """
            INSERT INTO audit_entries (id, source_event_id, occurred_at, recorded_at, actor_account_id, actor_role, action, target_type, target_id, details, source_service, trace_id)
            VALUES (:id, :sourceEventId, :occurredAt, :recordedAt, :actorAccountId, :actorRole, :action, :targetType, :targetId, CAST(:details AS jsonb), :sourceService, :traceId)
            ON CONFLICT (source_event_id) DO NOTHING
            """;

    private static final String FIND_BY_ID_SQL =
            "SELECT " + AuditEntryRowMapper.SELECT_COLUMNS + " FROM audit_entries WHERE id = :id";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditEntryRowMapper rowMapper;

    public AuditEntryRepositoryAdapter(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.rowMapper = new AuditEntryRowMapper(objectMapper);
    }

    @Override
    public boolean insertIfAbsent(AuditEntry entry) {
        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(entry.details());
        } catch (JacksonException e) {
            throw new IllegalStateException("Lỗi tuần tự hóa dữ kiện bổ sung", e);
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", entry.id());
        params.addValue("sourceEventId", entry.sourceEventId());
        params.addValue("occurredAt", OffsetDateTime.ofInstant(entry.occurredAt(), ZoneOffset.UTC));
        params.addValue("recordedAt", OffsetDateTime.ofInstant(entry.recordedAt(), ZoneOffset.UTC));

        if (entry.actor().accountId() != null) {
            params.addValue("actorAccountId", entry.actor().accountId());
        } else {
            params.addValue("actorAccountId", new SqlParameterValue(Types.OTHER, null));
        }

        params.addValue("actorRole", entry.actor().role());
        params.addValue("action", entry.action());
        params.addValue("targetType", entry.target().type());
        params.addValue("targetId", entry.target().id());
        params.addValue("details", detailsJson);
        params.addValue("sourceService", entry.sourceService());
        params.addValue("traceId", entry.traceId());

        int affectedRows = jdbcTemplate.update(INSERT_SQL, params);
        return affectedRows == 1;
    }

    @Override
    public Optional<AuditEntry> findById(AuditEntryId id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id.getValue());
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(FIND_BY_ID_SQL, params, rowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
