package vn.t3nexus.oauth2.infrastructure;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Một Postgres 16 dùng chung cho mọi test tích hợp trong một lần chạy (khởi động một lần, Ryuk dọn khi JVM thoát). Các test
 * dùng mã ngẫu nhiên và tự dọn nên không đụng nhau; Flyway chạy từ trống ở lần dựng ngữ cảnh đầu tiên.
 */
public final class PostgresContainerHolder {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private PostgresContainerHolder() {
    }
}
