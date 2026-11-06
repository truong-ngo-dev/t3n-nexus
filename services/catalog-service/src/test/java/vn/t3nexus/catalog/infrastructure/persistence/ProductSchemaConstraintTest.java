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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KT-04 và KT-08: migration {@code V2} trên Postgres thật (container cách ly). Mỗi ràng buộc có tên được thử vi phạm và
 * phải bị chặn đúng tên (ADR-0011); bỏ ràng buộc nào khỏi {@code V2} thì test tương ứng đỏ. Chốt cuối của tổ hợp duy nhất
 * và SKU duy nhất theo người bán (INV-CAT-92, 96).
 */
class ProductSchemaConstraintTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("catalog_db");

    private static JdbcTemplate jdbc;

    private static final UUID SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000bb");
    private static final UUID OTHER_SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000cc");

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
        jdbc.execute("TRUNCATE TABLE upload_slot, product_image, variant_combination_item, variant, product_attribute_option, product_attribute, product");
    }

    // ---- dữ liệu mẫu ----

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private UUID product(UUID seller, String title, String status, String description) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO product (id, seller_id, category_id, brand_id, title, description, status, version, "
                + "created_at, updated_at, published_at) VALUES (?, ?, ?, NULL, ?, ?, ?, 0, ?, ?, ?)",
                id, seller, UUID.randomUUID(), title, description, status, now(), now(),
                "DRAFT".equals(status) ? null : now());
        return id;
    }

    private UUID product() {
        return product(SELLER, "Áo thun", "DRAFT", null);
    }

    private UUID variant(UUID productId, UUID seller, String key, long price, String sku, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO variant (id, product_id, seller_id, combination_key, price, sku, status, created_at, "
                + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, productId, seller, key, price, sku, status,
                now(), now());
        return id;
    }

    private UUID variant(UUID productId, String key) {
        return variant(productId, SELLER, key, 100_000L, null, "ACTIVE");
    }

    private void attribute(UUID productId, UUID templateId, boolean axis, String value) {
        jdbc.update("INSERT INTO product_attribute (product_id, template_id, axis, value) VALUES (?, ?, ?, ?)",
                productId, templateId, axis, value);
    }

    private void attributeOption(UUID productId, UUID templateId, UUID optionId) {
        jdbc.update("INSERT INTO product_attribute_option (product_id, template_id, option_id) VALUES (?, ?, ?)",
                productId, templateId, optionId);
    }

    private void combinationItem(UUID variantId, UUID templateId, UUID optionId) {
        jdbc.update("INSERT INTO variant_combination_item (variant_id, template_id, option_id) VALUES (?, ?, ?)",
                variantId, templateId, optionId);
    }

    private static void assertBlockedBy(String constraint, Runnable action) {
        assertThatThrownBy(action::run)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains(constraint));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    // ---- cấu trúc ----

    @Test
    @DisplayName("Tên ràng buộc và chỉ mục đúng như data.md mục 10")
    void constraintNames() {
        List<String> names = jdbc.query(
                "SELECT conname FROM pg_constraint WHERE connamespace = 'public'::regnamespace "
                        + "UNION SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                (rs, i) -> rs.getString(1));
        assertThat(names).contains(
                "pk_product", "ck_product_status", "ck_product_title_length", "ck_product_description_length",
                "idx_product_seller",
                "pk_product_attribute", "fk_pa_product", "ck_product_attribute_axis_no_value",
                "pk_product_attribute_option", "fk_pao_attribute",
                "pk_variant", "fk_variant_product", "uq_variant_product_combination", "ck_variant_price",
                "ck_variant_sku_length", "ck_variant_status", "uq_variant_seller_sku", "idx_variant_product",
                "pk_variant_combination_item", "fk_vci_variant");
    }

    @Test
    @DisplayName("Không có khóa ngoại từ sản phẩm sang danh mục, thương hiệu, mẫu; từ tổ hợp sang giá trị trục")
    void noCrossAggregateForeignKeys() {
        List<String> foreignKeys = jdbc.query(
                "SELECT conname FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND contype = 'f'",
                (rs, i) -> rs.getString(1));
        assertThat(foreignKeys).doesNotContain("fk_product_category", "fk_product_brand", "fk_pa_template",
                "fk_pao_option", "fk_vci_option", "fk_vci_axis_value");
        UUID productId = product();
        attribute(productId, UUID.randomUUID(), false, "x");
        UUID variantId = variant(productId, "k");
        combinationItem(variantId, UUID.randomUUID(), UUID.randomUUID());
        assertThat(count("variant_combination_item")).isEqualTo(1);
    }

    // ---- sản phẩm ----

    @Test
    @DisplayName("Trạng thái sản phẩm: chỉ DRAFT, PUBLISHED, UNPUBLISHED")
    void productStatus() {
        for (String ok : new String[]{"DRAFT", "PUBLISHED", "UNPUBLISHED"}) {
            product(SELLER, "Áo thun", ok, null);
        }
        assertBlockedBy("ck_product_status", () -> product(SELLER, "Áo thun", "DELETED", null));
    }

    @Test
    @DisplayName("Tiêu đề 5 đến 200 ký tự (đếm ký tự, không đếm byte); mô tả tối đa 5.000, rỗng được")
    void productLengths() {
        product(SELLER, "a".repeat(5), "DRAFT", null);
        product(SELLER, "ă".repeat(200), "DRAFT", "đ".repeat(5_000));
        assertBlockedBy("ck_product_title_length", () -> product(SELLER, "abcd", "DRAFT", null));
        // Cận trên còn bị chính kiểu cột VARCHAR(200) chặn trước kiểm tra; chỉ cận dưới là việc của ràng buộc
        assertBlockedBy("value too long", () -> product(SELLER, "a".repeat(201), "DRAFT", null));
        assertBlockedBy("ck_product_description_length", () -> product(SELLER, "Áo thun", "DRAFT", "d".repeat(5_001)));
    }

    // ---- thuộc tính ----

    @Test
    @DisplayName("Thuộc tính làm trục không có giá trị rời; thuộc tính thường có thì được")
    void axisHasNoScalarValue() {
        UUID p = product();
        attribute(p, UUID.randomUUID(), false, "giá trị rời");
        attribute(p, UUID.randomUUID(), true, null);
        assertBlockedBy("ck_product_attribute_axis_no_value", () -> attribute(p, UUID.randomUUID(), true, "x"));
    }

    @Test
    @DisplayName("Mỗi mẫu một dòng cho mỗi sản phẩm; tùy chọn cần dòng cha, trùng khóa bị chặn")
    void attributeKeysAndParent() {
        UUID p = product();
        UUID t = UUID.randomUUID();
        attribute(p, t, true, null);
        assertBlockedBy("pk_product_attribute", () -> attribute(p, t, false, null));

        UUID o = UUID.randomUUID();
        attributeOption(p, t, o);
        assertBlockedBy("pk_product_attribute_option", () -> attributeOption(p, t, o));
        assertBlockedBy("fk_pao_attribute", () -> attributeOption(p, UUID.randomUUID(), UUID.randomUUID()));
        assertBlockedBy("fk_pa_product", () -> attribute(UUID.randomUUID(), t, false, "x"));
    }

    @Test
    @DisplayName("Xóa dòng thuộc tính xóa theo các tùy chọn của nó; xóa sản phẩm xóa theo thuộc tính (cùng aggregate)")
    void cascadesWithinProduct() {
        UUID p = product();
        UUID t = UUID.randomUUID();
        attribute(p, t, true, null);
        attributeOption(p, t, UUID.randomUUID());
        attributeOption(p, t, UUID.randomUUID());

        jdbc.update("DELETE FROM product_attribute WHERE product_id = ?", p);
        assertThat(count("product_attribute_option")).isZero();

        attribute(p, t, true, null);
        attributeOption(p, t, UUID.randomUUID());
        jdbc.update("DELETE FROM product WHERE id = ?", p);
        assertThat(count("product_attribute")).isZero();
        assertThat(count("product_attribute_option")).isZero();
    }

    // ---- biến thể ----

    @Test
    @DisplayName("Giá 1 đến 1 tỷ; trạng thái ACTIVE hoặc INACTIVE; SKU 1 đến 50 ký tự hoặc rỗng")
    void variantChecks() {
        UUID p = product();
        variant(p, SELLER, "a", 1L, null, "ACTIVE");
        variant(p, SELLER, "b", 1_000_000_000L, "x".repeat(50), "INACTIVE");
        assertBlockedBy("ck_variant_price", () -> variant(p, SELLER, "c", 0L, null, "ACTIVE"));
        assertBlockedBy("ck_variant_price", () -> variant(p, SELLER, "d", 1_000_000_001L, null, "ACTIVE"));
        assertBlockedBy("ck_variant_status", () -> variant(p, SELLER, "e", 1L, null, "DELETED"));
        assertBlockedBy("ck_variant_sku_length", () -> variant(p, SELLER, "f", 1L, "", "ACTIVE"));
        assertBlockedBy("value too long", () -> variant(p, SELLER, "g", 1L, "x".repeat(51), "ACTIVE"));
    }

    @Test
    @DisplayName("KT-08 tổ hợp duy nhất trong sản phẩm (INV-CAT-92); sản phẩm khác dùng lại được; khóa rỗng chỉ một")
    void combinationKeyUnique() {
        UUID p = product();
        UUID other = product();
        variant(p, "opt1,opt2");
        assertBlockedBy("uq_variant_product_combination", () -> variant(p, "opt1,opt2"));
        variant(other, "opt1,opt2");

        UUID plain = product();
        variant(plain, "");
        assertBlockedBy("uq_variant_product_combination", () -> variant(plain, ""));
    }

    @Test
    @DisplayName("KT-08 SKU duy nhất trong người bán qua mọi sản phẩm (INV-CAT-96); người bán khác dùng được; nhiều biến thể không SKU")
    void skuUniquePerSeller() {
        UUID first = product();
        UUID second = product();
        UUID theirs = product(OTHER_SELLER, "Áo thun", "DRAFT", null);
        variant(first, SELLER, "a", 1L, "SKU-1", "ACTIVE");
        assertBlockedBy("uq_variant_seller_sku", () -> variant(first, SELLER, "b", 1L, "SKU-1", "ACTIVE"));
        assertBlockedBy("uq_variant_seller_sku", () -> variant(second, SELLER, "a", 1L, "SKU-1", "ACTIVE"));
        variant(theirs, OTHER_SELLER, "a", 1L, "SKU-1", "ACTIVE");

        variant(first, SELLER, "c", 1L, null, "ACTIVE");
        variant(first, SELLER, "d", 1L, null, "ACTIVE");
        variant(second, SELLER, "c", 1L, null, "ACTIVE");
    }

    @Test
    @DisplayName("Biến thể cần sản phẩm; xóa sản phẩm còn biến thể bị chặn (RESTRICT)")
    void variantNeedsProductAndRestrictsDelete() {
        assertBlockedBy("fk_variant_product", () -> variant(UUID.randomUUID(), "a"));
        UUID p = product();
        variant(p, "a");
        assertBlockedBy("fk_variant_product", () -> jdbc.update("DELETE FROM product WHERE id = ?", p));
        assertThat(count("product")).isEqualTo(1);
    }

    @Test
    @DisplayName("Tổ hợp: mỗi biến thể một giá trị cho mỗi mẫu; cần biến thể; xóa biến thể xóa theo các cặp")
    void combinationItems() {
        UUID p = product();
        UUID v = variant(p, "a");
        UUID t = UUID.randomUUID();
        combinationItem(v, t, UUID.randomUUID());
        assertBlockedBy("pk_variant_combination_item", () -> combinationItem(v, t, UUID.randomUUID()));
        assertBlockedBy("fk_vci_variant", () -> combinationItem(UUID.randomUUID(), t, UUID.randomUUID()));

        jdbc.update("DELETE FROM variant WHERE id = ?", v);
        assertThat(count("variant_combination_item")).isZero();
    }

    @Test
    @DisplayName("Khóa chính UUID; cột version của sản phẩm là BIGINT; biến thể không có cột version")
    void typesAndVersionColumn() {
        List<String> versionColumns = jdbc.query("SELECT table_name || '.' || data_type FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND column_name = 'version' AND table_name IN ('product', 'variant')",
                (rs, i) -> rs.getString(1));
        assertThat(versionColumns).containsExactly("product.bigint");
        List<String> nonUuid = jdbc.query("SELECT table_name FROM information_schema.columns WHERE table_schema = 'public' "
                + "AND column_name = 'id' AND data_type <> 'uuid' AND table_name IN ('product', 'variant')",
                (rs, i) -> rs.getString(1));
        assertThat(nonUuid).isEmpty();
    }
}
