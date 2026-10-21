package vn.t3nexus.oauth2.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tham số lưu trữ của bảng bản ghi ủy quyền (ADR-0003 mục 8): làm mới token cập nhật cùng một dòng, nên mỗi trang chừa chỗ cho phiên bản
 * dòng mới (cập nhật tại chỗ, không phải cập nhật chỉ mục). Chạy toàn bộ migration trên một cơ sở dữ liệu trống của Postgres test
 * (Testcontainers), không đụng cơ sở dữ liệu dev.
 */
class OAuth2AuthorizationStorageTest {

    @Test
    void the_authorization_table_leaves_room_in_each_page_for_in_place_updates() throws Exception {
        PostgreSQLContainer postgres = PostgresContainerHolder.POSTGRES;
        String database = "migration_check_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        try {
            Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();

            try (Connection connection = DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT array_to_string(reloptions, ',') FROM pg_class WHERE relname = 'oauth2_authorization'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).contains("fillfactor=70");
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = admin.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            }
        }
    }
}
