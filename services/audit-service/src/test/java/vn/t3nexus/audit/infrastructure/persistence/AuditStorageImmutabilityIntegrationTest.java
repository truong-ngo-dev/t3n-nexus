package vn.t3nexus.audit.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import vn.t3nexus.audit.AuditServiceApplication;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KT-07 (ADR-0009): bản ghi kiểm toán bất biến ở lưu trữ, trên Postgres 16 thật (Testcontainers, không chạm cơ sở dữ liệu dev).
 * <br>Hai tài khoản dựng bằng chính tệp khởi tạo của docker-compose dev ({@code infra/audit/init-audit-roles.sql}): migration chạy
 * bằng {@code audit_owner} (chủ bảng), dịch vụ chạy bằng {@code audit_app} (chỉ INSERT, SELECT). Việc ngữ cảnh Spring khởi động
 * được với hai tài khoản đó cũng là phép kiểm "dịch vụ khởi động" của KT-07.
 * <br>Bảng không xóa được nên các ca không dọn dữ liệu: mỗi ca dùng mã sự kiện nguồn riêng và chỉ xét dòng của mình.
 */
@SpringBootTest(classes = AuditServiceApplication.class, properties = {
        "spring.main.web-application-type=none",
        "spring.jpa.hibernate.ddl-auto=validate",
        // Test này chỉ kiểm lưu trữ: không khởi động bộ nhận Kafka (không có Kafka để nối)
        "spring.kafka.listener.auto-startup=false"
})
class AuditStorageImmutabilityIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("audit_db")
            .withInitScript("init-audit-roles.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "audit_app");
        registry.add("spring.datasource.password", () -> "audit_app");
        registry.add("spring.flyway.user", () -> "audit_owner");
        registry.add("spring.flyway.password", () -> "audit_owner");
        registry.add("spring.flyway.placeholders.appRole", () -> "audit_app");
    }

    /** Kết nối của tài khoản dịch vụ (audit_app). */
    @Autowired JdbcTemplate app;

    private static final String INSERT = """
            INSERT INTO audit_entries (id, source_event_id, occurred_at, recorded_at, actor_account_id, actor_role,
                                       action, target_type, target_id, details, source_service, trace_id)
            VALUES (gen_random_uuid(), ?, now(), now(), ?, ?, 'SELLER_PROFILE_APPROVED', 'SELLER_PROFILE', 'p-1',
                    '{"profileVersion": 3}'::jsonb, 'seller-service', 'trace-1')
            """;

    private String insertRow() {
        String eventId = "ev-" + UUID.randomUUID();
        app.update(INSERT, eventId, UUID.randomUUID(), "ADMIN");
        return eventId;
    }

    // ---- tài khoản dịch vụ: đường bình thường ----

    @Test
    void serviceAccountCanInsertAndSelect() {
        String eventId = insertRow();

        String action = app.queryForObject("SELECT action FROM audit_entries WHERE source_event_id = ?", String.class, eventId);

        assertThat(action).isEqualTo("SELLER_PROFILE_APPROVED");
    }

    // ---- tài khoản dịch vụ: bị chặn bởi quyền (SQLSTATE 42501 = insufficient_privilege) ----

    @Test
    void serviceAccountCannotUpdate() {
        String eventId = insertRow();
        assertThat(sqlState(() -> app.update("UPDATE audit_entries SET action = 'X_Y' WHERE source_event_id = ?", eventId)))
                .isEqualTo("42501");
    }

    @Test
    void serviceAccountCannotDelete() {
        String eventId = insertRow();
        assertThat(sqlState(() -> app.update("DELETE FROM audit_entries WHERE source_event_id = ?", eventId)))
                .isEqualTo("42501");
    }

    @Test
    void serviceAccountCannotTruncate() {
        assertThat(sqlState(() -> app.execute("TRUNCATE TABLE audit_entries"))).isEqualTo("42501");
    }

    @Test
    void serviceAccountCannotAlterOrDropTable() {
        assertThat(sqlState(() -> app.execute("ALTER TABLE audit_entries ADD COLUMN extra int"))).isEqualTo("42501");
        assertThat(sqlState(() -> app.execute("DROP TABLE audit_entries"))).isEqualTo("42501");
    }

    @Test
    void serviceAccountCannotDisableTheTrigger() {
        assertThat(sqlState(() -> app.execute("ALTER TABLE audit_entries DISABLE TRIGGER ALL"))).isEqualTo("42501");
    }

    @Test
    void serviceAccountCannotCreateObjects() {
        assertThat(sqlState(() -> app.execute("CREATE TABLE public.leftover (id int)"))).isEqualTo("42501");
    }

    // ---- tài khoản chủ bảng: bị chặn bởi trigger (SQLSTATE 23001 = restrict_violation) ----

    @Test
    void ownerCannotUpdateDeleteOrTruncate() throws Exception {
        String eventId = insertRow();

        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "audit_owner", "audit_owner");
             Statement st = owner.createStatement()) {
            assertThat(sqlState(st, "UPDATE audit_entries SET action = 'X_Y' WHERE source_event_id = '" + eventId + "'")).isEqualTo("23001");
            assertThat(sqlState(st, "DELETE FROM audit_entries WHERE source_event_id = '" + eventId + "'")).isEqualTo("23001");
            assertThat(sqlState(st, "TRUNCATE TABLE audit_entries")).isEqualTo("23001");
        }

        Map<String, Object> row = app.queryForMap("SELECT action, details::text AS details FROM audit_entries WHERE source_event_id = ?", eventId);
        assertThat(row.get("action")).isEqualTo("SELLER_PROFILE_APPROVED");
        assertThat((String) row.get("details")).contains("profileVersion");
    }

    // ---- ràng buộc của bảng ----

    @Test
    void duplicateSourceEventIdIsRejected() {
        String eventId = insertRow();
        // SQLSTATE 23505 = unique_violation
        assertThat(sqlState(() -> app.update(INSERT, eventId, UUID.randomUUID(), "ADMIN"))).isEqualTo("23505");
    }

    @Test
    void actorAccountIsRequiredUnlessSystem() {
        // SQLSTATE 23514 = check_violation
        assertThat(sqlState(() -> app.update(INSERT, "ev-" + UUID.randomUUID(), null, "ADMIN"))).isEqualTo("23514");
        assertThat(app.update(INSERT, "ev-" + UUID.randomUUID(), null, "SYSTEM")).isEqualTo(1);
    }

    // ---- khởi động bằng hai tài khoản ----

    @Test
    void serviceRunsAsServiceAccountAndTableIsOwnedByOwnerAccount() throws Exception {
        assertThat(app.queryForObject("SELECT current_user", String.class)).isEqualTo("audit_app");
        assertThat(app.queryForObject("SELECT tableowner FROM pg_tables WHERE tablename = 'audit_entries'", String.class))
                .isEqualTo("audit_owner");

        // Lịch sử migration chỉ chủ bảng đọc được: tài khoản dịch vụ không có quyền trên bảng của Flyway
        assertThat(sqlState(() -> app.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class))).isEqualTo("42501");
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "audit_owner", "audit_owner");
             Statement st = owner.createStatement();
             var rs = st.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success AND version = '1'")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void serviceAccountHasOnlyInsertAndSelect() {
        List<String> granted = app.queryForList("""
                SELECT privilege_type FROM information_schema.role_table_grants
                WHERE grantee = 'audit_app' AND table_name = 'audit_entries' ORDER BY privilege_type
                """, String.class);
        assertThat(granted).containsExactly("INSERT", "SELECT");
    }

    @Test
    void listingIndexesExist() {
        List<String> indexes = app.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'audit_entries'", String.class);
        assertThat(indexes).contains("ix_audit_entries_occurred", "ix_audit_entries_actor", "ix_audit_entries_target", "ix_audit_entries_action");
    }

    // ---- hỗ trợ ----

    private static String sqlState(Runnable action) {
        try {
            action.run();
        } catch (DataAccessException e) {
            Throwable cause = e.getMostSpecificCause();
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
            throw e;
        }
        throw new AssertionError("Câu lệnh lẽ ra phải bị từ chối");
    }

    private static String sqlState(Statement statement, String sql) {
        assertThatThrownBy(() -> statement.execute(sql)).isInstanceOf(SQLException.class);
        try {
            statement.execute(sql);
        } catch (SQLException e) {
            return e.getSQLState();
        }
        throw new AssertionError("Câu lệnh lẽ ra phải bị từ chối");
    }
}
