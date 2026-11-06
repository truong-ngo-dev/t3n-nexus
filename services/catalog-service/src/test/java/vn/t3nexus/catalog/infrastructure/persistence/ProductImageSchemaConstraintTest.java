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
 * Kiểm thử ràng buộc lược đồ cho migration V3 (upload_slot, product_image) trên Postgres thật (Testcontainers).
 */
class ProductImageSchemaConstraintTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("catalog_db");

    private static JdbcTemplate jdbc;

    private static final UUID SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000bb");

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
        jdbc.execute("TRUNCATE TABLE upload_slot, product_image, variant_combination_item, variant, product_attribute_option, product_attribute, product CASCADE");
    }

    // ---- Dữ liệu mẫu ----

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private UUID product() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO product (id, seller_id, category_id, brand_id, title, description, status, version, "
                        + "created_at, updated_at) VALUES (?, ?, ?, NULL, ?, ?, ?, 0, ?, ?)",
                id, SELLER, UUID.randomUUID(), "Áo thun", null, "DRAFT", now(), now());
        return id;
    }

    private void insertProductImage(UUID id, UUID productId, String objectKey, String contentType, long sizeBytes, int position) {
        jdbc.update("INSERT INTO product_image (id, product_id, object_key, content_type, size_bytes, position) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, productId, objectKey, contentType, sizeBytes, position);
    }

    private void insertUploadSlot(UUID id, String purpose, UUID accountId, UUID targetId, String objectKey,
                                  String contentType, long declaredSize, Timestamp expiresAt, Timestamp createdAt) {
        jdbc.update("INSERT INTO upload_slot (id, purpose, account_id, target_id, object_key, content_type, declared_size, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, purpose, accountId, targetId, objectKey, contentType, declaredSize, expiresAt, createdAt);
    }

    private static void assertBlockedBy(String constraint, Runnable action) {
        assertThatThrownBy(action::run)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains(constraint));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    // ---- Ràng buộc product_image ----

    @Test
    @DisplayName("Khóa chính pk_product_image: không được trùng id")
    void pkProductImage() {
        UUID productId = product();
        UUID imageId = UUID.randomUUID();
        insertProductImage(imageId, productId, "images/1.jpg", "image/jpeg", 1024L, 0);

        assertBlockedBy("pk_product_image", () ->
                insertProductImage(imageId, productId, "images/2.jpg", "image/jpeg", 1024L, 1));
    }

    @Test
    @DisplayName("Khóa ngoại fk_product_image_product: product_id phải tồn tại trong bảng product")
    void fkProductImageProduct() {
        UUID nonExistentProductId = UUID.randomUUID();
        assertBlockedBy("fk_product_image_product", () ->
                insertProductImage(UUID.randomUUID(), nonExistentProductId, "images/1.jpg", "image/jpeg", 1024L, 0));
    }

    @Test
    @DisplayName("Ràng buộc uq_product_image_position: cùng product_id không được trùng position")
    void uqProductImagePosition() {
        UUID productId = product();
        insertProductImage(UUID.randomUUID(), productId, "images/1.jpg", "image/jpeg", 1024L, 0);

        assertBlockedBy("uq_product_image_position", () ->
                insertProductImage(UUID.randomUUID(), productId, "images/2.jpg", "image/jpeg", 2048L, 0));
    }

    @Test
    @DisplayName("Ràng buộc uq_product_image_object_key: không được trùng object_key ở hai sản phẩm khác nhau")
    void uqProductImageObjectKey() {
        UUID p1 = product();
        UUID p2 = product();
        insertProductImage(UUID.randomUUID(), p1, "images/shared.jpg", "image/jpeg", 1024L, 0);

        assertBlockedBy("uq_product_image_object_key", () ->
                insertProductImage(UUID.randomUUID(), p2, "images/shared.jpg", "image/jpeg", 1024L, 0));
    }

    @Test
    @DisplayName("Ràng buộc ck_product_image_position: position phải >= 0")
    void ckProductImagePosition() {
        UUID productId = product();
        assertBlockedBy("ck_product_image_position", () ->
                insertProductImage(UUID.randomUUID(), productId, "images/1.jpg", "image/jpeg", 1024L, -1));
    }

    @Test
    @DisplayName("Ràng buộc ck_product_image_size: size_bytes phải > 0")
    void ckProductImageSize() {
        UUID productId = product();
        assertBlockedBy("ck_product_image_size", () ->
                insertProductImage(UUID.randomUUID(), productId, "images/1.jpg", "image/jpeg", 0L, 0));
    }

    @Test
    @DisplayName("Ràng buộc NOT NULL của object_key: không được null")
    void notNullProductImageObjectKey() {
        UUID productId = product();
        assertBlockedBy("null value", () ->
                insertProductImage(UUID.randomUUID(), productId, null, "image/jpeg", 1024L, 0));
    }

    @Test
    @DisplayName("Khóa ngoại CASCADE: xóa product thì các product_image liên quan bị xóa theo")
    void fkCascadeProductDelete() {
        UUID productId = product();
        insertProductImage(UUID.randomUUID(), productId, "images/1.jpg", "image/jpeg", 1024L, 0);
        insertProductImage(UUID.randomUUID(), productId, "images/2.jpg", "image/jpeg", 2048L, 1);

        assertThat(count("product_image")).isEqualTo(2);

        jdbc.update("DELETE FROM product WHERE id = ?", productId);

        assertThat(count("product_image")).isZero();
    }

    // ---- Ràng buộc upload_slot ----

    @Test
    @DisplayName("Khóa chính pk_upload_slot: không được trùng id")
    void pkUploadSlot() {
        UUID slotId = UUID.randomUUID();
        insertUploadSlot(slotId, "PRODUCT_IMAGE", UUID.randomUUID(), UUID.randomUUID(),
                "uploads/1.jpg", "image/jpeg", 1024L, now(), now());

        assertBlockedBy("pk_upload_slot", () ->
                insertUploadSlot(slotId, "PRODUCT_IMAGE", UUID.randomUUID(), UUID.randomUUID(),
                        "uploads/2.jpg", "image/jpeg", 2048L, now(), now()));
    }

    @Test
    @DisplayName("Ràng buộc uq_upload_slot_object_key: không được trùng object_key")
    void uqUploadSlotObjectKey() {
        insertUploadSlot(UUID.randomUUID(), "PRODUCT_IMAGE", UUID.randomUUID(), UUID.randomUUID(),
                "uploads/same.jpg", "image/jpeg", 1024L, now(), now());

        assertBlockedBy("uq_upload_slot_object_key", () ->
                insertUploadSlot(UUID.randomUUID(), "PRODUCT_IMAGE", UUID.randomUUID(), UUID.randomUUID(),
                        "uploads/same.jpg", "image/jpeg", 2048L, now(), now()));
    }

    @Test
    @DisplayName("Ràng buộc ck_upload_slot_size: declared_size phải > 0")
    void ckUploadSlotSize() {
        assertBlockedBy("ck_upload_slot_size", () ->
                insertUploadSlot(UUID.randomUUID(), "PRODUCT_IMAGE", UUID.randomUUID(), UUID.randomUUID(),
                        "uploads/zero.jpg", "image/jpeg", 0L, now(), now()));
    }

    // ---- Ca đối chứng hợp lệ ----

    @Test
    @DisplayName("Đối chứng product_image: hai ảnh cùng sản phẩm khác position khác object_key chèn thành công; cùng position khác sản phẩm chèn thành công")
    void validProductImageInsert() {
        UUID p1 = product();
        UUID p2 = product();

        // Hai ảnh cùng sản phẩm khác position khác object_key
        insertProductImage(UUID.randomUUID(), p1, "images/p1_pos0.jpg", "image/jpeg", 1024L, 0);
        insertProductImage(UUID.randomUUID(), p1, "images/p1_pos1.jpg", "image/jpeg", 2048L, 1);

        // Cùng position nhưng khác sản phẩm
        insertProductImage(UUID.randomUUID(), p2, "images/p2_pos0.jpg", "image/jpeg", 1024L, 0);

        assertThat(count("product_image")).isEqualTo(3);
    }

    @Test
    @DisplayName("Đối chứng upload_slot: chèn bản ghi hợp lệ thành công")
    void validUploadSlotInsert() {
        insertUploadSlot(UUID.randomUUID(), "PRODUCT_IMAGE", UUID.randomUUID(), UUID.randomUUID(),
                "uploads/valid1.jpg", "image/jpeg", 1024L, now(), now());
        insertUploadSlot(UUID.randomUUID(), "PRODUCT_IMAGE", UUID.randomUUID(), UUID.randomUUID(),
                "uploads/valid2.jpg", "image/png", 5000L, now(), now());

        assertThat(count("upload_slot")).isEqualTo(2);
    }
}
