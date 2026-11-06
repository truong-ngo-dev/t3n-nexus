package vn.t3nexus.catalog.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.NameKey;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryLevel;

/**
 * Feature 06: kiểm chính tệp {@code infra/seed/catalog.sql} (chạy bằng {@code psql} trong container Postgres cách ly, không
 * chép nội dung sang test), rồi kiểm dữ liệu đã nạp theo đúng quy tắc nghiệp vụ N1 đến N10 của {@code flow.md}: dựng lại
 * qua chính hàm của miền và đọc qua API của feature 05.
 */
class CatalogSeedTest extends CatalogApiTestBase {

    private static final Path SCRIPT = Path.of("../../infra/seed/catalog.sql").toAbsolutePath().normalize();
    private static final String TARGET = "/tmp/catalog-seed.sql";
    private static final String[] TABLES = {
            "brand", "attribute_template", "attribute_option", "category", "category_attribute_assignment"};

    @BeforeAll
    static void copyScript() {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(SCRIPT), TARGET);
    }

    // ───────────── Chạy script ─────────────

    private ExecResult runSeed(String database) throws Exception {
        return POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d", database, "-f", TARGET);
    }

    private ExecResult runSeed() throws Exception {
        return runSeed(POSTGRES.getDatabaseName());
    }

    private void seedOk() throws Exception {
        ExecResult r = runSeed();
        assertThat(r.getExitCode()).as(r.getStderr()).isZero();
        assertThat(r.getStdout()).contains("seed-catalog: created");
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private List<Long> counts() {
        return Stream.of(TABLES).map(this::count).toList();
    }

    private String allIds() {
        return jdbcTemplate.queryForObject(
                "SELECT string_agg(id::text, ',' ORDER BY id) FROM (SELECT id FROM brand UNION ALL SELECT id FROM attribute_template "
                        + "UNION ALL SELECT id FROM attribute_option UNION ALL SELECT id FROM category) x", String.class);
    }

    private void insertBrand(String name) {
        jdbcTemplate.update("INSERT INTO brand (id, name, name_key, status, version, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'ACTIVE', 0, now(), now())", UUID.randomUUID(), name, NameKey.of(name).key());
    }

    // ───────────── KT-01 đến KT-05: script ─────────────

    @Test
    void seedsExactDataset() throws Exception {
        seedOk();

        assertThat(counts()).containsExactly(10L, 13L, 35L, 22L, 57L);
        for (String table : List.of("brand", "attribute_template", "attribute_option", "category")) {
            assertThat(count(table + " WHERE status <> 'ACTIVE'")).as(table + " đang dùng").isZero();
        }
        for (String table : List.of("brand", "attribute_template", "category")) {
            assertThat(count(table + " WHERE version <> 0")).as(table + " version 0").isZero();
            assertThat(count(table + " WHERE created_at <> updated_at")).isZero();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT created_at) FROM (SELECT created_at FROM brand UNION ALL SELECT created_at FROM attribute_template "
                        + "UNION ALL SELECT created_at FROM category) x", Long.class))
                .as("mọi thực thể cùng một thời điểm tạo").isEqualTo(1L);
    }

    @Test
    void secondRunAddsNothing() throws Exception {
        seedOk();
        String idsBefore = allIds();

        ExecResult second = runSeed();

        assertThat(second.getExitCode()).isZero();
        assertThat(second.getStdout()).contains("seed-catalog: exists").doesNotContain("seed-catalog: created");
        assertThat(counts()).containsExactly(10L, 13L, 35L, 22L, 57L);
        assertThat(allIds()).as("không đổi mã nào").isEqualTo(idsBefore);
    }

    @Test
    void concurrentRunsSeedExactlyOnce() throws Exception {
        CompletableFuture<ExecResult> a = CompletableFuture.supplyAsync(() -> unchecked(() -> runSeed()));
        CompletableFuture<ExecResult> b = CompletableFuture.supplyAsync(() -> unchecked(() -> runSeed()));
        ExecResult ra = a.get();
        ExecResult rb = b.get();

        assertThat(ra.getExitCode()).as(ra.getStderr()).isZero();
        assertThat(rb.getExitCode()).as(rb.getStderr()).isZero();
        List<String> outputs = List.of(ra.getStdout(), rb.getStdout());
        assertThat(outputs.stream().filter(o -> o.contains("seed-catalog: created")).count()).isEqualTo(1);
        assertThat(outputs.stream().filter(o -> o.contains("seed-catalog: exists")).count()).isEqualTo(1);
        assertThat(counts()).containsExactly(10L, 13L, 35L, 22L, 57L);
    }

    @Test
    void existingBrandBlocksSeed() throws Exception {
        insertBrand("Hãng của quản trị");

        ExecResult r = runSeed();

        assertThat(r.getExitCode()).isZero();
        assertThat(r.getStdout()).contains("seed-catalog: exists");
        assertThat(counts()).containsExactly(1L, 0L, 0L, 0L, 0L);
    }

    @Test
    void existingCategoryBlocksSeed() throws Exception {
        jdbcTemplate.update("INSERT INTO category (id, parent_id, level, name, name_key, sort_order, status, version, created_at, updated_at) "
                + "VALUES (?, NULL, 1, 'Danh mục riêng', 'danh muc rieng', 0, 'ACTIVE', 0, now(), now())", UUID.randomUUID());

        ExecResult r = runSeed();

        assertThat(r.getExitCode()).isZero();
        assertThat(r.getStdout()).contains("seed-catalog: exists");
        assertThat(counts()).containsExactly(0L, 0L, 0L, 1L, 0L);
    }

    @Test
    void missingTablesStopWithoutWriting() throws Exception {
        String database = "seed_empty_" + Long.toHexString(System.nanoTime());
        jdbcTemplate.execute("CREATE DATABASE " + database);
        try {
            ExecResult r = runSeed(database);

            assertThat(r.getExitCode()).isNotZero();
            assertThat(r.getStderr()).contains("thiếu bảng").contains("brand").contains("category_attribute_assignment");
            assertThat(r.getStdout()).doesNotContain("seed-catalog: created");
        } finally {
            jdbcTemplate.execute("DROP DATABASE IF EXISTS " + database);
        }
    }

    @Test
    void failureInLastStatementRollsBackEverything() throws Exception {
        jdbcTemplate.execute("ALTER TABLE category_attribute_assignment ADD CONSTRAINT ck_seed_must_fail CHECK (false)");
        try {
            ExecResult r = runSeed();

            assertThat(r.getExitCode()).isNotZero();
            assertThat(r.getStderr()).contains("ck_seed_must_fail");
            assertThat(counts()).as("cả năm bảng vẫn trống").containsExactly(0L, 0L, 0L, 0L, 0L);
        } finally {
            jdbcTemplate.execute("ALTER TABLE category_attribute_assignment DROP CONSTRAINT IF EXISTS ck_seed_must_fail");
        }
    }

    // ───────────── KT-06: khóa tên ─────────────

    @Test
    void nameKeysEqualTheDomainNameKey() throws Exception {
        seedOk();

        int accented = 0;
        for (String table : List.of("brand", "category")) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT name, name_key FROM " + table);
            assertThat(rows).isNotEmpty();
            for (Map<String, Object> row : rows) {
                String name = (String) row.get("name");
                NameKey expected = NameKey.of(name);
                assertThat(row.get("name_key")).as(table + ": " + name).isEqualTo(expected.key());
                assertThat(name).as("tên đã ở dạng hiển thị (không khoảng trắng thừa)").isEqualTo(expected.display());
                if (!name.equals(row.get("name_key"))) accented++;
            }
        }
        assertThat(accented).as("có tên có dấu hoặc chữ hoa để phép so có ý nghĩa").isGreaterThan(10);
        // Các trường hợp khó mà dữ liệu dựa vào
        assertThat(NameKey.of("Đồ dùng nhà bếp").key()).isEqualTo("do dung nha bep");
        assertThat(NameKey.of("Lock&Lock").key()).isEqualTo("lock&lock");
        assertThat(NameKey.of("  THỜI   TRANG ").key()).isEqualTo("thoi trang");
    }

    // ───────────── KT-07: dựng lại qua miền, quy tắc N1 đến N8 ─────────────

    @Test
    void datasetReplaysThroughTheDomain() throws Exception {
        seedOk();
        ChangeContext ctx = new ChangeContext(new CatalogActor(UUID.randomUUID(), "ADMIN"), UUID.randomUUID(), Instant.now());

        // Mẫu và tùy chọn: tạo lại bằng hàm của miền; thất bại nếu sai dạng mã, sai đơn vị, sai kiểu
        Map<UUID, InputType> typeOf = new LinkedHashMap<>();
        Map<UUID, Integer> optionCount = new LinkedHashMap<>();
        Set<InputType> typesSeen = new HashSet<>();
        List<Map<String, Object>> templates = jdbcTemplate.queryForList(
                "SELECT id, name, display_name, hint, input_type, unit FROM attribute_template ORDER BY name");
        for (Map<String, Object> t : templates) {
            UUID id = (UUID) t.get("id");
            InputType type = InputType.valueOf((String) t.get("input_type"));
            AttributeTemplate template = AttributeTemplate.create(AttributeTemplateId.of(id), (String) t.get("name"),
                    (String) t.get("display_name"), (String) t.get("hint"), type, (String) t.get("unit"), ctx);
            assertThat(template.getDisplayName()).as("tên hiển thị đã chuẩn: " + t.get("name")).isEqualTo(t.get("display_name"));
            List<Map<String, Object>> options = jdbcTemplate.queryForList(
                    "SELECT id, value, display_value, sort_order FROM attribute_option WHERE template_id = ? ORDER BY sort_order", id);
            int position = 0;
            for (Map<String, Object> o : options) {
                template.addOption(AttributeOptionId.of((UUID) o.get("id")), (String) o.get("value"),
                        (String) o.get("display_value"), ctx);
                assertThat(o.get("sort_order")).as("thứ tự tùy chọn " + o.get("value")).isEqualTo(position++);
            }
            assertThat(options.isEmpty()).as("chỉ mẫu kiểu chọn có tùy chọn: " + t.get("name")).isEqualTo(!type.isSelect());
            assertThat(template.isUsable()).as("mẫu dùng được: " + t.get("name")).isTrue();
            typeOf.put(id, type);
            optionCount.put(id, options.size());
            typesSeen.add(type);
        }
        assertThat(typesSeen).as("N8: đủ sáu kiểu nhập").containsExactlyInAnyOrder(InputType.values());

        // Cây: tạo lại từ cha xuống con, rồi thay danh sách gán của lá bằng chính dòng gán đã nạp
        Map<UUID, Category> built = new LinkedHashMap<>();
        Map<UUID, Integer> levelOf = new LinkedHashMap<>();
        Map<UUID, Integer> childCount = new LinkedHashMap<>();
        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
                "SELECT id, parent_id, level, name, name_key, sort_order FROM category ORDER BY level, sort_order");
        for (Map<String, Object> c : categories) {
            UUID id = (UUID) c.get("id");
            UUID parentId = (UUID) c.get("parent_id");
            int level = ((Number) c.get("level")).intValue();
            Category category = parentId == null
                    ? Category.createRoot(CategoryId.of(id), (String) c.get("name"), (Integer) c.get("sort_order"), ctx)
                    : Category.createChild(CategoryId.of(id), (String) c.get("name"), CategoryId.of(parentId),
                            CategoryLevel.of(levelOf.get(parentId)), (Integer) c.get("sort_order"), ctx);
            assertThat(category.getNameKey()).isEqualTo(c.get("name_key"));
            assertThat(category.getLevel().getValue()).as("cấp theo cha: " + c.get("name")).isEqualTo(level);
            built.put(id, category);
            levelOf.put(id, level);
            if (parentId != null) childCount.merge(parentId, 1, Integer::sum);
        }
        for (Map.Entry<UUID, Integer> e : levelOf.entrySet()) {
            if (e.getValue() < 3) {
                assertThat(childCount.getOrDefault(e.getKey(), 0)).as("N1: nhánh đủ xuống lá: " + built.get(e.getKey()).getName())
                        .isGreaterThan(0);
            }
        }

        Map<UUID, List<CategoryAttributeAssignment>> assignmentsOf = new LinkedHashMap<>();
        Map<UUID, Boolean> leafHasSingleSelect = new LinkedHashMap<>();
        boolean sawRequired = false, sawOptional = false, sawConstraint = false, sawEmpty = false;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT category_id, template_id, required, display_order, constraints::text AS constraints "
                        + "FROM category_attribute_assignment ORDER BY category_id, display_order");
        for (Map<String, Object> row : rows) {
            UUID categoryId = (UUID) row.get("category_id");
            UUID templateId = (UUID) row.get("template_id");
            assertThat(levelOf.get(categoryId)).as("N3: chỉ gán ở cấp ba").isEqualTo(3);
            InputType type = typeOf.get(templateId);
            String raw = (String) row.get("constraints");
            AttributeConstraints constraints = objectMapper.readValue(raw, AttributeConstraints.class);
            boolean required = (Boolean) row.get("required");
            assignmentsOf.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(
                    CategoryAttributeAssignment.create(AttributeTemplateId.of(templateId), type, required,
                            (Integer) row.get("display_order"), constraints));
            JsonNode node = objectMapper.readTree(raw);
            if (constraints.min() != null && constraints.max() != null) {
                assertThat(constraints.min()).as("N5: min không lớn hơn max").isLessThanOrEqualTo(constraints.max());
            }
            if (constraints.maxSelections() != null) {
                assertThat(constraints.maxSelections()).as("N5: maxSelections không vượt số tùy chọn")
                        .isLessThanOrEqualTo(optionCount.get(templateId));
            }
            sawRequired |= required;
            sawOptional |= !required;
            sawConstraint |= node.size() > 0;
            sawEmpty |= node.size() == 0;
            if (type == InputType.SINGLE_SELECT) leafHasSingleSelect.put(categoryId, true);
        }
        for (Map.Entry<UUID, Integer> e : levelOf.entrySet()) {
            if (e.getValue() == 3) {
                List<CategoryAttributeAssignment> list = assignmentsOf.get(e.getKey());
                assertThat(list).as("lá có gán thuộc tính: " + built.get(e.getKey()).getName()).isNotEmpty();
                built.get(e.getKey()).replaceAssignments(list, ctx);
                assertThat(leafHasSingleSelect.get(e.getKey())).as("N7: lá có mẫu chọn một: " + built.get(e.getKey()).getName()).isTrue();
            }
        }
        assertThat(List.of(sawRequired, sawOptional, sawConstraint, sawEmpty)).as("N8: đủ các dạng dòng gán").containsOnly(true);
    }

    // ───────────── KT-08: đọc qua API ─────────────

    @Test
    void apiReadsSeededData() throws Exception {
        seedOk();
        UUID viewer = UUID.randomUUID();

        JsonNode tree = json(mockMvc.perform(get("/api/categories").with(user(viewer))).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(names(tree)).containsExactly("Thời trang", "Điện tử", "Nhà cửa và đời sống");
        assertThat(names(tree.get(0).path("children"))).containsExactly("Thời trang nam", "Thời trang nữ");
        assertThat(names(tree.get(1).path("children"))).containsExactly("Điện thoại", "Phụ kiện điện thoại", "Âm thanh");
        assertThat(names(tree.get(0).path("children").get(0).path("children")))
                .containsExactly("Áo thun nam", "Áo sơ mi nam", "Quần jean nam");
        int leaves = 0;
        for (JsonNode l1 : tree) for (JsonNode l2 : l1.path("children")) leaves += l2.path("children").size();
        assertThat(leaves).isEqualTo(13);

        JsonNode adminTree = json(mockMvc.perform(get("/api/admin/categories/tree").with(admin(viewer))).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(names(adminTree)).containsExactly("Thời trang", "Điện tử", "Nhà cửa và đời sống");

        UUID smartphone = leafId("Smartphone");
        JsonNode attrs = json(mockMvc.perform(get("/api/categories/" + smartphone + "/attributes").with(user(viewer)))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(names(attrs, "name")).containsExactly("color", "storage", "ram", "screen_size", "battery_capacity",
                "warranty_months", "release_date", "is_waterproof", "origin", "model");
        JsonNode color = attrs.get(0);
        assertThat(color.path("displayName").asText()).isEqualTo("Màu sắc");
        assertThat(color.path("required").asBoolean()).isTrue();
        assertThat(color.path("options")).hasSize(8);
        assertThat(color.path("options").get(0).path("value").asText()).isEqualTo("den");
        assertThat(color.path("options").get(0).path("displayValue").asText()).isEqualTo("Đen");
        JsonNode screen = attrs.get(3);
        assertThat(screen.path("unit").asText()).isEqualTo("inch");
        assertThat(screen.path("constraints").path("min").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(screen.path("constraints").path("max").decimalValue()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(attrs.get(6).path("constraints").path("inputPrecision").asText()).isEqualTo("MONTH");

        JsonNode brands = json(mockMvc.perform(get("/api/brands?size=50").with(user(viewer))).andExpect(status().isOk()).andReturn()).path("data");
        assertThat(brands.path("total").asLong()).isEqualTo(10);
        assertThat(names(brands.path("items"))).contains("Lock&Lock", "Samsung", "Sunhouse");
    }

    // ───────────── KT-09: thao tác quản trị trên dữ liệu mẫu ─────────────

    @Test
    void adminOperatesOnSeededDataLikeAnyOther() throws Exception {
        seedOk();
        UUID admin = UUID.randomUUID();

        // Khóa tên khớp: cùng tên khác dấu, hoa thường, khoảng trắng thì trùng
        mockMvc.perform(post("/api/admin/categories").with(admin(admin)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"  THOI   TRANG \"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));
        UUID menFashion = categoryId("Thời trang nam");
        mockMvc.perform(post("/api/admin/categories").with(admin(admin)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"ÁO THUN NAM\",\"parentId\":\"" + menFashion + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));

        // Đổi tên danh mục mẫu
        UUID tshirt = leafId("Áo thun nam");
        mockMvc.perform(put("/api/admin/categories/" + tshirt).with(admin(admin)).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Áo phông nam\"}"))
                .andExpect(status().isOk());

        // Thêm tùy chọn vào một mẫu đã nạp; giá trị đã có thì trùng
        UUID colorTemplate = jdbcTemplate.queryForObject("SELECT id FROM attribute_template WHERE name = 'color'", UUID.class);
        mockMvc.perform(post("/api/admin/attribute-templates/" + colorTemplate + "/options").with(admin(admin))
                        .contentType(APPLICATION_JSON).content("{\"value\":\"tim\",\"displayValue\":\"Tím\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/attribute-templates/" + colorTemplate + "/options").with(admin(admin))
                        .contentType(APPLICATION_JSON).content("{\"value\":\"den\",\"displayValue\":\"Đen khác\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTRIBUTE_OPTION_VALUE_TAKEN"));

        // Ngừng dùng một lá mẫu thì cây dành cho người dùng không còn lá đó
        UUID dress = leafId("Váy nữ");
        mockMvc.perform(post("/api/admin/categories/" + dress + "/deactivate").with(admin(admin))).andExpect(status().isOk());
        JsonNode tree = json(mockMvc.perform(get("/api/categories").with(user(UUID.randomUUID()))).andExpect(status().isOk()).andReturn()).path("data");
        List<String> womenLeaves = names(tree.get(0).path("children").get(1).path("children"));
        assertThat(womenLeaves).containsExactly("Áo thun nữ");
    }

    // ───────────── KT-10: mã định danh, hộp thư, migration ─────────────

    @Test
    void idsAreUuidV7NoOutboxAndNoMigrationHoldsSeedData() throws Exception {
        seedOk();

        for (String table : List.of("brand", "attribute_template", "attribute_option", "category")) {
            assertThat(count(table + " WHERE substring(id::text FROM 15 FOR 1) <> '7'")).as(table + ": UUID phiên bản 7").isZero();
        }
        assertThat(count("outbox_events")).as("dữ liệu khởi tạo không phát event").isZero();

        Pattern insertIntoSeedTable = Pattern.compile(
                "INSERT\\s+INTO\\s+(brand|attribute_template|attribute_option|category|category_attribute_assignment)\\b",
                Pattern.CASE_INSENSITIVE);
        Path migrations = Path.of("src/main/resources/db/migration");
        try (Stream<Path> files = Files.list(migrations)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".sql")).toList()) {
                assertThat(insertIntoSeedTable.matcher(Files.readString(file)).find())
                        .as("migration không chứa dữ liệu mẫu: " + file.getFileName()).isFalse();
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    // ───────────── Hỗ trợ ─────────────

    private UUID leafId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ? AND level = 3", UUID.class, name);
    }

    private UUID categoryId(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", UUID.class, name);
    }

    private static List<String> names(JsonNode array) {
        return names(array, "name");
    }

    private static List<String> names(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.path(field).asText()));
        return out;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static <T> T unchecked(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
