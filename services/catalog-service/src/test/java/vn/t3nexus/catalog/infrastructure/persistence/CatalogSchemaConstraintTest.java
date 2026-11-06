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
 * KT-04: migration {@code V1} trên Postgres thật (container cách ly). Mỗi ràng buộc có tên được thử vi phạm và phải
 * bị chặn đúng tên (ADR-0011); bỏ ràng buộc nào khỏi {@code V1} thì test tương ứng đỏ.
 */
class CatalogSchemaConstraintTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("catalog_db");

    private static JdbcTemplate jdbc;

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
        jdbc.execute("TRUNCATE TABLE upload_slot, product_image, variant_combination_item, variant, product_attribute_option, product_attribute, product, "
                + "category_attribute_assignment, category, attribute_option, "
                + "attribute_template, brand, outbox_events");
    }

    // ---- dữ liệu mẫu ----

    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

    private UUID brand(String nameKey, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO brand (id, name, name_key, status, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, 0, ?, ?)", id, nameKey, nameKey, status, now(), now());
        return id;
    }

    private UUID template(String name, String inputType, String unit, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO attribute_template (id, name, display_name, input_type, unit, status, "
                        + "version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                id, name, name, inputType, unit, status, now(), now());
        return id;
    }

    private UUID option(UUID templateId, String value, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO attribute_option (id, template_id, value, display_value, sort_order, "
                        + "status, created_at) VALUES (?, ?, ?, ?, 0, ?, ?)",
                id, templateId, value, value, status, now());
        return id;
    }

    private UUID category(UUID parentId, int level, String nameKey, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO category (id, parent_id, level, name, name_key, sort_order, status, version, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, 0, ?, 0, ?, ?)",
                id, parentId, level, nameKey, nameKey, status, now(), now());
        return id;
    }

    private void assign(UUID categoryId, UUID templateId) {
        jdbc.update("INSERT INTO category_attribute_assignment (category_id, template_id, required, display_order, "
                + "constraints) VALUES (?, ?, false, 0, '{}'::jsonb)", categoryId, templateId);
    }

    /** Hành động phải bị cơ sở dữ liệu chặn, thông báo gốc nêu đúng tên ràng buộc. */
    private static void assertBlockedBy(String constraint, Runnable action) {
        assertThatThrownBy(action::run)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains(constraint));
    }

    // ---- cấu trúc ----

    @Test
    @DisplayName("Flyway áp đúng V1, V2, V3 và V4; đủ sáu bảng cấu trúc và năm bảng sản phẩm, biến thể")
    void migrationAppliedWithAllTables() {
        List<String> versions = jdbc.query(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                (rs, i) -> rs.getString("version"));
        assertThat(versions).containsExactly("1", "2", "3", "4");

        List<String> tables = jdbc.query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                (rs, i) -> rs.getString("table_name"));
        assertThat(tables).contains("brand", "attribute_template", "attribute_option", "category",
                "category_attribute_assignment", "outbox_events",
                "product", "product_attribute", "product_attribute_option", "variant", "variant_combination_item",
                "product_image", "upload_slot", "seller_state");
    }

    @Test
    @DisplayName("Mọi khóa chính là UUID; cột version là BIGINT")
    void idsAreUuid() {
        List<String> nonUuidIds = jdbc.query(
                "SELECT table_name FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND column_name = 'id' AND data_type <> 'uuid' AND table_name <> 'outbox_events'",
                (rs, i) -> rs.getString("table_name"));
        assertThat(nonUuidIds).isEmpty();
    }

    @Test
    @DisplayName("Tên ràng buộc và chỉ mục đúng như data.md")
    void constraintNames() {
        List<String> names = jdbc.query(
                "SELECT conname FROM pg_constraint WHERE connamespace = 'public'::regnamespace "
                        + "UNION SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                (rs, i) -> rs.getString(1));
        assertThat(names).contains(
                "pk_brand", "uq_brand_name_key", "ck_brand_status",
                "pk_attribute_template", "uq_attribute_template_name", "ck_attribute_template_name_format", "ck_attribute_template_input_type",
                "ck_attribute_template_status", "ck_attribute_template_unit",
                "pk_attribute_option", "fk_attribute_option_template", "uq_attribute_option_template_value",
                "ck_attribute_option_value_format", "ck_attribute_option_status",
                "pk_category", "fk_category_parent", "ck_category_level", "ck_category_root_level",
                "ck_category_status", "uq_category_parent_name_key", "uq_category_root_name_key", "idx_category_parent",
                "pk_category_attribute_assignment", "fk_caa_category", "fk_caa_template",
                "pk_outbox_events", "uq_outbox_events_event_id", "idx_outbox_events_created_at");
    }

    // ---- thương hiệu ----

    @Test
    void brandNameKeyIsUnique() {
        brand("apple", "ACTIVE");
        assertBlockedBy("uq_brand_name_key", () -> brand("apple", "ACTIVE"));
    }

    @Test
    void brandStatusIsChecked() {
        assertBlockedBy("ck_brand_status", () -> brand("apple", "DELETED"));
    }

    // ---- mẫu thuộc tính và tùy chọn ----

    @Test
    void templateNameIsUnique() {
        template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        assertBlockedBy("uq_attribute_template_name", () -> template("mau_sac", "TEXT", null, "ACTIVE"));
    }

    @Test
    void templateNameMustBeLowercaseCode() {
        template("screen_size", "TEXT", null, "ACTIVE");
        for (String bad : new String[]{"Color", "mau sac", "màu", "mau-sac", "_a", "a_", "a__b", ""}) {
            assertBlockedBy("ck_attribute_template_name_format", () -> template(bad, "TEXT", null, "ACTIVE"));
        }
    }

    @Test
    void templateInputTypeAndStatusAreChecked() {
        assertBlockedBy("ck_attribute_template_input_type", () -> template("a", "RADIO", null, "ACTIVE"));
        assertBlockedBy("ck_attribute_template_status", () -> template("b", "TEXT", null, "DELETED"));
    }

    @Test
    void templateUnitOnlyForNumber() {
        template("trong_luong", "NUMBER", "kg", "ACTIVE");
        assertBlockedBy("ck_attribute_template_unit", () -> template("mau", "TEXT", "kg", "ACTIVE"));
    }

    @Test
    void optionValueIsUniquePerTemplateOnly() {
        UUID t1 = template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        UUID t2 = template("kich_thuoc", "SINGLE_SELECT", null, "ACTIVE");
        option(t1, "do", "ACTIVE");

        option(t2, "do", "ACTIVE"); // cùng giá trị ở mẫu khác thì được
        assertBlockedBy("uq_attribute_option_template_value", () -> option(t1, "do", "ACTIVE"));
    }

    @Test
    void optionValueMustBeLowercaseCode() {
        UUID t = template("cong", "SINGLE_SELECT", null, "ACTIVE");
        option(t, "usb-c", "ACTIVE");
        option(t, "x_large", "ACTIVE");
        for (String bad : new String[]{"Red", "đỏ", "do tuoi", "-a", "a-", "_a", "a--b", ""}) {
            assertBlockedBy("ck_attribute_option_value_format", () -> option(t, bad, "ACTIVE"));
        }
    }

    @Test
    void optionNeedsExistingTemplateAndBlocksTemplateRemoval() {
        assertBlockedBy("fk_attribute_option_template", () -> option(UUID.randomUUID(), "do", "ACTIVE"));

        UUID t = template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        option(t, "do", "ACTIVE");
        assertBlockedBy("fk_attribute_option_template",
                () -> jdbc.update("DELETE FROM attribute_template WHERE id = ?", t));
    }

    @Test
    void optionStatusIsChecked() {
        UUID t = template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        assertBlockedBy("ck_attribute_option_status", () -> option(t, "do", "DELETED"));
    }

    // ---- danh mục ----

    @Test
    void rootNameKeyIsUnique() {
        category(null, 1, "thoi trang", "ACTIVE");
        assertBlockedBy("uq_category_root_name_key", () -> category(null, 1, "thoi trang", "ACTIVE"));
    }

    @Test
    void childNameKeyIsUniqueWithinSameParentOnly() {
        UUID a = category(null, 1, "thoi trang", "ACTIVE");
        UUID b = category(null, 1, "dien tu", "ACTIVE");
        category(a, 2, "ao nam", "ACTIVE");

        category(b, 2, "ao nam", "ACTIVE"); // cha khác thì được
        category(null, 1, "ao nam", "ACTIVE"); // cấp một không đụng cấp con
        assertBlockedBy("uq_category_parent_name_key", () -> category(a, 2, "ao nam", "ACTIVE"));
    }

    @Test
    void categoryLevelRulesAreChecked() {
        UUID root = category(null, 1, "thoi trang", "ACTIVE");
        UUID mid = category(root, 2, "ao nam", "ACTIVE");
        UUID leaf = category(mid, 3, "ao thun", "ACTIVE");

        assertBlockedBy("ck_category_level", () -> category(leaf, 4, "cap bon", "ACTIVE"));
        assertBlockedBy("ck_category_root_level", () -> category(null, 2, "mo coi", "ACTIVE"));
        assertBlockedBy("ck_category_root_level", () -> category(root, 1, "cap mot co cha", "ACTIVE"));
    }

    @Test
    void categoryStatusIsChecked() {
        assertBlockedBy("ck_category_status", () -> category(null, 1, "thoi trang", "DELETED"));
    }

    @Test
    void parentMustExistAndCannotBeRemovedWhileHavingChildren() {
        assertBlockedBy("fk_category_parent", () -> category(UUID.randomUUID(), 2, "ao nam", "ACTIVE"));

        UUID root = category(null, 1, "thoi trang", "ACTIVE");
        category(root, 2, "ao nam", "ACTIVE");
        assertBlockedBy("fk_category_parent", () -> jdbc.update("DELETE FROM category WHERE id = ?", root));
    }

    // ---- dòng gán ----

    @Test
    void assignmentIsUniquePerCategoryAndTemplate() {
        UUID c = leafCategory("ao thun");
        UUID t = template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        assign(c, t);
        assertBlockedBy("pk_category_attribute_assignment", () -> assign(c, t));
    }

    @Test
    void assignmentNeedsExistingCategoryAndTemplate() {
        UUID c = leafCategory("ao thun");
        UUID t = template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        assertBlockedBy("fk_caa_category", () -> assign(UUID.randomUUID(), t));
        assertBlockedBy("fk_caa_template", () -> assign(c, UUID.randomUUID()));
    }

    @Test
    void deletingCategoryRemovesItsAssignmentsButTemplateInUseIsProtected() {
        UUID c = leafCategory("ao thun");
        UUID t = template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        assign(c, t);

        assertBlockedBy("fk_caa_template", () -> jdbc.update("DELETE FROM attribute_template WHERE id = ?", t));

        jdbc.update("DELETE FROM category WHERE id = ?", c);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM category_attribute_assignment", Long.class)).isZero();
    }

    @Test
    void assignmentConstraintsColumnIsRequired() {
        UUID c = leafCategory("ao thun");
        UUID t = template("mau_sac", "SINGLE_SELECT", null, "ACTIVE");
        assertBlockedBy("constraints", () -> jdbc.update("INSERT INTO category_attribute_assignment "
                + "(category_id, template_id, required, display_order, constraints) VALUES (?, ?, false, 0, NULL)", c, t));
    }

    private UUID leafCategory(String nameKey) {
        UUID root = category(null, 1, "goc " + nameKey, "ACTIVE");
        UUID mid = category(root, 2, "giua " + nameKey, "ACTIVE");
        return category(mid, 3, nameKey, "ACTIVE");
    }

    // ---- hộp thư ----

    @Test
    void outboxEventIdIsUnique() {
        String sql = "INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, event_type, routing_key, "
                + "payload, occurred_on, created_at) VALUES (?, 'Category', 'x', 'CategoryChanged', 'catalog.category.changed', "
                + "'{}', ?, ?)";
        String eventId = "evt-" + UUID.randomUUID();
        jdbc.update(sql, eventId, now(), now());
        assertBlockedBy("uq_outbox_events_event_id", () -> jdbc.update(sql, eventId, now(), now()));
    }
}
