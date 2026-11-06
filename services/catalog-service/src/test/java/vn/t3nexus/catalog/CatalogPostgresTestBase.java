package vn.t3nexus.catalog;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Lớp cơ sở dùng chung cho các test tích hợp cơ sở dữ liệu PostgreSQL của catalog-service.
 * <br>Container cách ly (không đụng cơ sở dữ liệu dev): Flyway áp {@code V1} lên đó, ứng dụng và Flyway dùng chung một tài khoản.
 */
public abstract class CatalogPostgresTestBase {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("catalog_db");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
