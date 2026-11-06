package vn.t3nexus.audit;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Lớp cơ sở dùng chung cho các test tích hợp cơ sở dữ liệu PostgreSQL.
 * <br>Bảo đảm cấu hình tài khoản phân quyền dịch vụ (audit_app) và migration (audit_owner).
 */
public abstract class AuditPostgresTestBase {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
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
}
