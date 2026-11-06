package vn.t3nexus.seller;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Lớp cơ sở dùng chung cho các test tích hợp cơ sở dữ liệu PostgreSQL của seller-service.
 * <br>Bảo đảm cấu hình một tài khoản duy nhất cho cả ứng dụng và Flyway trên container cách ly.
 */
public abstract class SellerPostgresTestBase {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("seller_db");

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
