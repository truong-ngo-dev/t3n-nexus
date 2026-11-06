package vn.t3nexus.catalog.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử ràng buộc lược đồ cho migration V4 (publish_product: ck_product_published_at, seller_state) trên Postgres thật (Testcontainers).
 */
class PublishSchemaConstraintTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("catalog_db");

    private static JdbcTemplate jdbc;

    private static final UUID SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

    private static final String UPSERT_SELLER_STATE_SQL =
            "INSERT INTO seller_state (account_id, status, version, updated_at) VALUES (?, ?, ?, now()) "
                    + "ON CONFLICT (account_id) DO UPDATE SET "
                    + "status = EXCLUDED.status, version = EXCLUDED.version, updated_at = EXCLUDED.updated_at "
                    + "WHERE seller_state.version < EXCLUDED.version";

    @BeforeAll
    static void migrate() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void cleanUp() {
        jdbc.execute("TRUNCATE TABLE seller_state, upload_slot, product_image, variant_combination_item, variant, product_attribute_option, product_attribute, product CASCADE");
    }

    // ---- Dữ liệu mẫu ----

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private UUID insertProductWithStatus(String status, Timestamp publishedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO product (id, seller_id, category_id, brand_id, title, description, status, published_at, version, "
                        + "created_at, updated_at) VALUES (?, ?, ?, NULL, ?, ?, ?, ?, 0, ?, ?)",
                id, SELLER, UUID.randomUUID(), "Áo thun", null, status, publishedAt, now(), now());
        return id;
    }

    private void insertSellerState(UUID accountId, String status, long version, Timestamp updatedAt) {
        jdbc.update("INSERT INTO seller_state (account_id, status, version, updated_at) VALUES (?, ?, ?, ?)",
                accountId, status, version, updatedAt);
    }

    private static void assertBlockedBy(String constraint, Runnable action) {
        assertThatThrownBy(action::run)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains(constraint));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    // ---- Ràng buộc ck_product_published_at ----

    @Test
    @DisplayName("Ràng buộc ck_product_published_at (a): status DRAFT kèm published_at khác rỗng bị từ chối")
    void ckProductPublishedAtDraftWithTimestampRejected() {
        assertBlockedBy("ck_product_published_at", () ->
                insertProductWithStatus("DRAFT", now()));
    }

    @Test
    @DisplayName("Ràng buộc ck_product_published_at (b): status PUBLISHED với published_at rỗng bị từ chối")
    void ckProductPublishedAtPublishedWithNullRejected() {
        assertBlockedBy("ck_product_published_at", () ->
                insertProductWithStatus("PUBLISHED", null));
    }

    @Test
    @DisplayName("Ràng buộc ck_product_published_at (c): status UNPUBLISHED với published_at rỗng bị từ chối")
    void ckProductPublishedAtUnpublishedWithNullRejected() {
        assertBlockedBy("ck_product_published_at", () ->
                insertProductWithStatus("UNPUBLISHED", null));
    }

    @Test
    @DisplayName("Đối chứng ck_product_published_at: DRAFT + null, PUBLISHED + có giá trị, UNPUBLISHED + có giá trị chèn thành công")
    void ckProductPublishedAtValidCases() {
        insertProductWithStatus("DRAFT", null);
        insertProductWithStatus("PUBLISHED", now());
        insertProductWithStatus("UNPUBLISHED", now());

        assertThat(count("product")).isEqualTo(3);
    }

    @Test
    @DisplayName("Cột published_at cho phép rỗng với dòng DRAFT cũ: UPDATE một dòng DRAFT giữ rỗng không lỗi")
    void updateDraftProductKeepPublishedAtNull() {
        UUID productId = insertProductWithStatus("DRAFT", null);

        int updatedRows = jdbc.update("UPDATE product SET title = ?, updated_at = ? WHERE id = ?",
                "Áo thun mới cập nhật", now(), productId);

        assertThat(updatedRows).isEqualTo(1);
        String updatedTitle = jdbc.queryForObject("SELECT title FROM product WHERE id = ?", String.class, productId);
        assertThat(updatedTitle).isEqualTo("Áo thun mới cập nhật");
        Timestamp publishedAt = jdbc.queryForObject("SELECT published_at FROM product WHERE id = ?", Timestamp.class, productId);
        assertThat(publishedAt).isNull();
    }

    // ---- Ràng buộc seller_state ----

    @Test
    @DisplayName("Khóa chính pk_seller_state: trùng account_id bị từ chối")
    void pkSellerState() {
        UUID accountId = UUID.randomUUID();
        insertSellerState(accountId, "APPROVED", 0, now());

        assertBlockedBy("pk_seller_state", () ->
                insertSellerState(accountId, "REJECTED", 1, now()));
    }

    @Test
    @DisplayName("Ràng buộc ck_seller_state_status: status PENDING bị từ chối")
    void ckSellerStateStatusPendingRejected() {
        assertBlockedBy("ck_seller_state_status", () ->
                insertSellerState(UUID.randomUUID(), "PENDING", 0, now()));
    }

    @Test
    @DisplayName("Đối chứng ck_seller_state_status: APPROVED và REJECTED chèn được")
    void ckSellerStateStatusValidCases() {
        insertSellerState(UUID.randomUUID(), "APPROVED", 0, now());
        insertSellerState(UUID.randomUUID(), "REJECTED", 1, now());

        assertThat(count("seller_state")).isEqualTo(2);
    }

    @Test
    @DisplayName("Ràng buộc ck_seller_state_version: version = -1 bị từ chối")
    void ckSellerStateVersionNegativeRejected() {
        assertBlockedBy("ck_seller_state_version", () ->
                insertSellerState(UUID.randomUUID(), "APPROVED", -1, now()));
    }

    @Test
    @DisplayName("Đối chứng ck_seller_state_version: version = 0 chèn được")
    void ckSellerStateVersionZeroAllowed() {
        insertSellerState(UUID.randomUUID(), "APPROVED", 0, now());

        assertThat(count("seller_state")).isEqualTo(1);
    }

    @Test
    @DisplayName("Ràng buộc NOT NULL của status trên seller_state: status null bị từ chối")
    void notNullSellerStateStatus() {
        assertBlockedBy("null value", () ->
                insertSellerState(UUID.randomUUID(), null, 0, now()));
    }

    // ---- Câu upsert ghi đè theo phiên bản ----

    @Test
    @DisplayName("Câu upsert seller_state theo version: chèn mới trả 1 dòng; cùng version lần 2 trả 0 và không đổi; version thấp hơn trả 0 và không đổi; version cao hơn trả 1 và đổi status")
    void upsertSellerStateVersionCheck() {
        UUID accountId = UUID.randomUUID();

        // Chèn mới trả 1 dòng
        int inserted = jdbc.update(UPSERT_SELLER_STATE_SQL, accountId, "APPROVED", 2L);
        assertThat(inserted).isEqualTo(1);

        String status1 = jdbc.queryForObject("SELECT status FROM seller_state WHERE account_id = ?", String.class, accountId);
        Long version1 = jdbc.queryForObject("SELECT version FROM seller_state WHERE account_id = ?", Long.class, accountId);
        assertThat(status1).isEqualTo("APPROVED");
        assertThat(version1).isEqualTo(2L);

        // Cùng version lần hai trả 0 dòng và không đổi
        int sameVersion = jdbc.update(UPSERT_SELLER_STATE_SQL, accountId, "REJECTED", 2L);
        assertThat(sameVersion).isZero();

        String statusSame = jdbc.queryForObject("SELECT status FROM seller_state WHERE account_id = ?", String.class, accountId);
        Long versionSame = jdbc.queryForObject("SELECT version FROM seller_state WHERE account_id = ?", Long.class, accountId);
        assertThat(statusSame).isEqualTo("APPROVED");
        assertThat(versionSame).isEqualTo(2L);

        // Version thấp hơn trả 0 dòng và không đổi
        int lowerVersion = jdbc.update(UPSERT_SELLER_STATE_SQL, accountId, "REJECTED", 1L);
        assertThat(lowerVersion).isZero();

        String statusLower = jdbc.queryForObject("SELECT status FROM seller_state WHERE account_id = ?", String.class, accountId);
        Long versionLower = jdbc.queryForObject("SELECT version FROM seller_state WHERE account_id = ?", Long.class, accountId);
        assertThat(statusLower).isEqualTo("APPROVED");
        assertThat(versionLower).isEqualTo(2L);

        // Version cao hơn trả 1 dòng và đổi cả status
        int higherVersion = jdbc.update(UPSERT_SELLER_STATE_SQL, accountId, "REJECTED", 3L);
        assertThat(higherVersion).isEqualTo(1);

        String statusHigher = jdbc.queryForObject("SELECT status FROM seller_state WHERE account_id = ?", String.class, accountId);
        Long versionHigher = jdbc.queryForObject("SELECT version FROM seller_state WHERE account_id = ?", Long.class, accountId);
        assertThat(statusHigher).isEqualTo("REJECTED");
        assertThat(versionHigher).isEqualTo(3L);
    }
}
