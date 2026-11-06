package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử toàn diện API quản lý danh mục và gán thuộc tính.
 */
class CategoryApiTest extends CatalogApiTestBase {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // ==========================================
    // Các hàm trợ giúp
    // ==========================================

    /**
     * Tạo danh mục qua API quản trị (POST /api/admin/categories) và trả về ID.
     */
    private UUID createCategory(String name, UUID parentId) throws Exception {
        String body = parentId == null
                ? """
                  { "name": "%s" }
                  """.formatted(name)
                : """
                  { "name": "%s", "parentId": "%s" }
                  """.formatted(name, parentId);

        MvcResult result = mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        return UUID.fromString(json(result).path("data").path("id").asText());
    }

    /**
     * Dựng nhánh 3 cấp: Cấp 1 -> Cấp 2 -> Cấp 3 và trả về ID của lá (cấp 3).
     */
    private UUID buildThreeLevelBranch(String prefix) throws Exception {
        UUID root = createCategory(prefix + " L1", null);
        UUID sub = createCategory(prefix + " L2", root);
        return createCategory(prefix + " L3", sub);
    }

    /**
     * Tạo mẫu thuộc tính qua API quản trị (POST /api/admin/attribute-templates).
     */
    private UUID createTemplate(String name, String displayName, String inputType) throws Exception {
        String body = """
                {
                    "name": "%s",
                    "displayName": "%s",
                    "inputType": "%s"
                }
                """.formatted(name, displayName, inputType);

        MvcResult result = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        return UUID.fromString(json(result).path("data").path("id").asText());
    }

    /**
     * Thêm tùy chọn cho mẫu thuộc tính (POST /api/admin/attribute-templates/{id}/options).
     */
    private void addOption(UUID templateId, String value, String displayValue) throws Exception {
        String body = """
                {
                    "value": "%s",
                    "displayValue": "%s"
                }
                """.formatted(value, displayValue);

        mockMvc.perform(post("/api/admin/attribute-templates/" + templateId + "/options")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Ngừng dùng mẫu thuộc tính (POST /api/admin/attribute-templates/{id}/deactivate).
     */
    private void deactivateTemplate(UUID templateId) throws Exception {
        mockMvc.perform(post("/api/admin/attribute-templates/" + templateId + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * Gán thuộc tính cho danh mục qua API quản trị (PUT /api/admin/categories/{id}/attributes).
     */
    private MvcResult replaceAssignments(UUID categoryId, String itemsJson) throws Exception {
        String body = """
                {
                    "items": %s
                }
                """.formatted(itemsJson);

        return mockMvc.perform(put("/api/admin/categories/" + categoryId + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private int countOutbox(String routingKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ?", Integer.class, routingKey);
        return count != null ? count : 0;
    }

    private int countAuditWithPayload(String action) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = 'audit.action.recorded' AND payload LIKE ?",
                Integer.class, "%" + action + "%");
        return count != null ? count : 0;
    }

    private int countCategoryRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM category", Integer.class);
        return count != null ? count : 0;
    }

    private int countTotalOutbox() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
        return count != null ? count : 0;
    }

    // ==========================================
    // 28 Test Cases
    // ==========================================

    /**
     * 1. unauthenticatedIsRejected: mọi đường không token -> 401.
     */
    @Test
    void unauthenticatedIsRejected() throws Exception {
        UUID randomId = UUID.randomUUID();

        // 2 GET công khai
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/categories/" + randomId + "/attributes")).andExpect(status().isUnauthorized());

        // GET admin tree
        mockMvc.perform(get("/api/admin/categories/tree")).andExpect(status().isUnauthorized());

        // GET assignments
        mockMvc.perform(get("/api/admin/categories/" + randomId + "/assignments")).andExpect(status().isUnauthorized());

        // POST tạo
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Test" }
                                """))
                .andExpect(status().isUnauthorized());

        // PUT đổi tên
        mockMvc.perform(put("/api/admin/categories/" + randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "New Name" }
                                """))
                .andExpect(status().isUnauthorized());

        // PUT reorder
        mockMvc.perform(put("/api/admin/categories/reorder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "categoryIds": [] }
                                """))
                .andExpect(status().isUnauthorized());

        // DELETE
        mockMvc.perform(delete("/api/admin/categories/" + randomId)).andExpect(status().isUnauthorized());

        // Deactivate
        mockMvc.perform(post("/api/admin/categories/" + randomId + "/deactivate")).andExpect(status().isUnauthorized());

        // Activate
        mockMvc.perform(post("/api/admin/categories/" + randomId + "/activate")).andExpect(status().isUnauthorized());

        // PUT attributes
        mockMvc.perform(put("/api/admin/categories/" + randomId + "/attributes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [] }
                                """))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 2. nonAdminCannotUseAdminEndpoints: token USER gọi các admin endpoints -> 403 CATALOG_ROLE_NOT_ALLOWED; bảng category vẫn rỗng sau POST.
     */
    @Test
    void nonAdminCannotUseAdminEndpoints() throws Exception {
        UUID randomId = UUID.randomUUID();

        // GET admin tree
        mockMvc.perform(get("/api/admin/categories/tree").with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // GET assignments
        mockMvc.perform(get("/api/admin/categories/" + randomId + "/assignments").with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // POST tạo danh mục
        mockMvc.perform(post("/api/admin/categories").with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Thời trang" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));
        assertThat(countCategoryRows()).isZero();

        // PUT đổi tên
        mockMvc.perform(put("/api/admin/categories/" + randomId).with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Đổi tên" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // PUT reorder
        mockMvc.perform(put("/api/admin/categories/reorder").with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "categoryIds": [] }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // DELETE
        mockMvc.perform(delete("/api/admin/categories/" + randomId).with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // Deactivate
        mockMvc.perform(post("/api/admin/categories/" + randomId + "/deactivate").with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // Activate
        mockMvc.perform(post("/api/admin/categories/" + randomId + "/activate").with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // PUT attributes
        mockMvc.perform(put("/api/admin/categories/" + randomId + "/attributes").with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [] }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));
    }

    /**
     * 3. nonAdminCanReadPublicEndpoints: token USER GET /api/categories và GET /api/categories/{id}/attributes -> 200.
     */
    @Test
    void nonAdminCanReadPublicEndpoints() throws Exception {
        UUID leafId = buildThreeLevelBranch("Công khai");

        mockMvc.perform(get("/api/categories").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/categories/" + leafId + "/attributes").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 4. createRootReturns201WithVersionZero: ADMIN POST {"name":"Thời trang"} -> 201, level=1, parentId null, sortOrder=0, status=ACTIVE, version=0;
     * outbox_events có 2 hàng (1 kiểm toán chứa CATEGORY_CREATED, 1 catalog.category.changed).
     */
    @Test
    void createRootReturns201WithVersionZero() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Thời trang" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Thời trang"))
                .andExpect(jsonPath("$.data.parentId").doesNotExist())
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.sortOrder").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0));

        assertThat(countOutbox("audit.action.recorded")).isEqualTo(1);
        assertThat(countAuditWithPayload("CATEGORY_CREATED")).isEqualTo(1);
        assertThat(countOutbox("catalog.category.changed")).isEqualTo(1);
    }

    /**
     * 5. createChildLevels: tạo cấp một, hai, ba -> level 1, 2, 3, parentId đúng;
     * cấp bốn (cha là cấp ba) -> 409 CATEGORY_MAX_DEPTH_EXCEEDED, bảng category vẫn 3 hàng, outbox_events không đổi sau lần lỗi.
     */
    @Test
    void createChildLevels() throws Exception {
        UUID l1 = createCategory("Cấp 1", null);
        UUID l2 = createCategory("Cấp 2", l1);
        UUID l3 = createCategory("Cấp 3", l2);

        // Kiểm tra thông tin cấp 3
        mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].level").value(1))
                .andExpect(jsonPath("$.data[0].children[0].level").value(2))
                .andExpect(jsonPath("$.data[0].children[0].children[0].level").value(3));

        int rowsBefore = countCategoryRows();
        int outboxBefore = countTotalOutbox();

        // Cố gắng tạo cấp 4
        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Cấp 4", "parentId": "%s" }
                                """.formatted(l3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_MAX_DEPTH_EXCEEDED"));

        assertThat(countCategoryRows()).isEqualTo(rowsBefore).isEqualTo(3);
        assertThat(countTotalOutbox()).isEqualTo(outboxBefore);
    }

    /**
     * 6. createUnderUnknownParentIsNotFound: parentId là UUID ngẫu nhiên -> 404 CATEGORY_NOT_FOUND.
     */
    @Test
    void createUnderUnknownParentIsNotFound() throws Exception {
        UUID unknownParentId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Con vô thừa nhận", "parentId": "%s" }
                                """.formatted(unknownParentId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    /**
     * 7. createRejectsBlankAndTooLongName: tên rỗng, toàn dấu cách, 101 ký tự -> 400 CATALOG_FIELD_INVALID; không thêm hàng.
     */
    @Test
    void createRejectsBlankAndTooLongName() throws Exception {
        int initialRows = countCategoryRows();

        // Tên rỗng
        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        // Toàn khoảng trắng
        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "     " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        // 101 ký tự
        String name101 = "a".repeat(101);
        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s" }
                                """.formatted(name101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        assertThat(countCategoryRows()).isEqualTo(initialRows);
    }

    /**
     * 8. newCategoryGoesLastAmongSiblings: tạo 3 danh mục cấp một A, B, C -> sortOrder 0, 1, 2; con của A đầu tiên có sortOrder=0.
     */
    @Test
    void newCategoryGoesLastAmongSiblings() throws Exception {
        UUID a = createCategory("A", null);
        UUID b = createCategory("B", null);
        UUID c = createCategory("C", null);
        UUID childA = createCategory("Child A", a);

        mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(a.toString()))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].children[0].id").value(childA.toString()))
                .andExpect(jsonPath("$.data[0].children[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].id").value(b.toString()))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data[2].id").value(c.toString()))
                .andExpect(jsonPath("$.data[2].sortOrder").value(2));
    }

    /**
     * 9. duplicateNameInSameParentIsConflict: tạo "Áo nam" dưới cha P; tạo "AO   NAM" dưới P -> 409 CATEGORY_NAME_TAKEN;
     * tạo "Áo nam" cấp một trùng "áo nam" cấp một -> 409; bảng category không đổi, outbox_events không đổi sau lần lỗi.
     */
    @Test
    void duplicateNameInSameParentIsConflict() throws Exception {
        UUID parent = createCategory("Cha", null);
        createCategory("Áo nam", parent);

        int rowsBefore = countCategoryRows();
        int outboxBefore = countTotalOutbox();

        // Trùng dưới cùng cha P (chuẩn hóa không dấu, không hoa thường, rút gọn khoảng trắng)
        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "AO   NAM", "parentId": "%s" }
                                """.formatted(parent)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));

        assertThat(countCategoryRows()).isEqualTo(rowsBefore);
        assertThat(countTotalOutbox()).isEqualTo(outboxBefore);

        // Trùng ở cấp 1
        createCategory("Giày dép", null);
        int rowsBeforeRoot = countCategoryRows();
        int outboxBeforeRoot = countTotalOutbox();

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "giày dép" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));

        assertThat(countCategoryRows()).isEqualTo(rowsBeforeRoot);
        assertThat(countTotalOutbox()).isEqualTo(outboxBeforeRoot);
    }

    /**
     * 10. sameNameUnderDifferentParentsIsAllowed: "Phụ kiện" dưới hai cha khác nhau -> cả hai 201.
     */
    @Test
    void sameNameUnderDifferentParentsIsAllowed() throws Exception {
        UUID p1 = createCategory("Thời trang Nam", null);
        UUID p2 = createCategory("Thời trang Nữ", null);

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Phụ kiện", "parentId": "%s" }
                                """.formatted(p1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Phụ kiện", "parentId": "%s" }
                                """.formatted(p2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    /**
     * 11. closedParentStillAcceptsChildren: deactivate cha rồi tạo con -> 201.
     */
    @Test
    void closedParentStillAcceptsChildren() throws Exception {
        UUID root = createCategory("Điện máy", null);

        // Deactivate cha
        mockMvc.perform(post("/api/admin/categories/" + root + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk());

        // Tạo con dưới cha đã deactivate
        mockMvc.perform(post("/api/admin/categories")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Tủ lạnh", "parentId": "%s" }
                                """.formatted(root)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.parentId").value(root.toString()));
    }

    /**
     * 12. renameChangesNameAndBumpsVersion: PUT đổi tên -> 200, name đổi, version=1; kiểm toán chứa CATEGORY_UPDATED.
     */
    @Test
    void renameChangesNameAndBumpsVersion() throws Exception {
        UUID id = createCategory("Tên Cũ", null);

        int auditBefore = countAuditWithPayload("CATEGORY_UPDATED");

        mockMvc.perform(put("/api/admin/categories/" + id)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Tên Mới" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Tên Mới"))
                .andExpect(jsonPath("$.data.version").value(1));

        assertThat(countAuditWithPayload("CATEGORY_UPDATED")).isEqualTo(auditBefore + 1);
        assertThat(countOutbox("catalog.category.changed")).isGreaterThanOrEqualTo(2);
    }

    /**
     * 13. renameToSiblingsNameIsConflictButOwnSpellingIsAllowed: đổi tên thành tên của anh em (khác dấu, hoa thường) -> 409 CATEGORY_NAME_TAKEN;
     * đổi thành "ÁO NAM" của chính nó -> 200; đổi thành tên đang dùng ở cha khác -> 200.
     */
    @Test
    void renameToSiblingsNameIsConflictButOwnSpellingIsAllowed() throws Exception {
        UUID parent1 = createCategory("Cha 1", null);
        UUID parent2 = createCategory("Cha 2", null);

        UUID c1 = createCategory("Áo nam", parent1);
        UUID c2 = createCategory("Quần nam", parent1);
        createCategory("Giày dép", parent2);

        // Đổi c2 thành tên của anh em c1 (khác hoa thường / dấu)
        mockMvc.perform(put("/api/admin/categories/" + c2)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "ao nam" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_TAKEN"));

        // Đổi c1 thành "ÁO NAM" của chính nó (chỉ đổi cách viết hoa)
        mockMvc.perform(put("/api/admin/categories/" + c1)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "ÁO NAM" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("ÁO NAM"));

        // Đổi c2 thành tên đang dùng ở cha khác ("Giày dép")
        mockMvc.perform(put("/api/admin/categories/" + c2)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Giày dép" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Giày dép"));
    }

    /**
     * 14. unknownAndMalformedIds: PUT đổi tên, DELETE, deactivate, activate, GET assignments, GET /api/categories/{id}/attributes
     * với UUID ngẫu nhiên -> 404 CATEGORY_NOT_FOUND; id "khong-phai-uuid" -> 400 INVALID_PARAMETER.
     */
    @Test
    void unknownAndMalformedIds() throws Exception {
        UUID randomId = UUID.randomUUID();

        // 404 CATEGORY_NOT_FOUND
        mockMvc.perform(put("/api/admin/categories/" + randomId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Tên Mới" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(delete("/api/admin/categories/" + randomId)
                        .with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(post("/api/admin/categories/" + randomId + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(post("/api/admin/categories/" + randomId + "/activate")
                        .with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(get("/api/admin/categories/" + randomId + "/assignments")
                        .with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(get("/api/categories/" + randomId + "/attributes")
                        .with(user(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        // 400 INVALID_PARAMETER
        String malformed = "khong-phai-uuid";

        mockMvc.perform(put("/api/admin/categories/" + malformed)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Tên Mới" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(delete("/api/admin/categories/" + malformed)
                        .with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(post("/api/admin/categories/" + malformed + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(post("/api/admin/categories/" + malformed + "/activate")
                        .with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/admin/categories/" + malformed + "/assignments")
                        .with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mockMvc.perform(get("/api/categories/" + malformed + "/attributes")
                        .with(user(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /**
     * 15. deactivateThenActivate: deactivate -> 200 INACTIVE version=1 (kiểm toán CATEGORY_DEACTIVATED);
     * lần nữa -> 409 CATALOG_INVALID_STATE (version vẫn 1, outbox_events không đổi);
     * activate -> 200 ACTIVE version=2; lần nữa -> 409 CATALOG_INVALID_STATE.
     */
    @Test
    void deactivateThenActivate() throws Exception {
        UUID id = createCategory("Mẹ và bé", null);

        // Deactivate lần 1
        mockMvc.perform(post("/api/admin/categories/" + id + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(countAuditWithPayload("CATEGORY_DEACTIVATED")).isEqualTo(1);

        int outboxAfterFirstDeactivate = countTotalOutbox();

        // Deactivate lần 2 -> 409
        mockMvc.perform(post("/api/admin/categories/" + id + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));
        assertThat(countTotalOutbox()).isEqualTo(outboxAfterFirstDeactivate);

        // Activate lần 1
        mockMvc.perform(post("/api/admin/categories/" + id + "/activate")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2));

        int outboxAfterFirstActivate = countTotalOutbox();

        // Activate lần 2 -> 409
        mockMvc.perform(post("/api/admin/categories/" + id + "/activate")
                        .with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));
        assertThat(countTotalOutbox()).isEqualTo(outboxAfterFirstActivate);
    }

    /**
     * 16. deactivatingParentDoesNotCascade: ngừng cấp một; con cấp hai vẫn status=ACTIVE và version=0 (xem qua GET /api/admin/categories/tree).
     */
    @Test
    void deactivatingParentDoesNotCascade() throws Exception {
        UUID root = createCategory("Cấp 1", null);
        createCategory("Cấp 2", root);

        // Ngừng cấp 1
        mockMvc.perform(post("/api/admin/categories/" + root + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk());

        // Kiểm tra qua cây admin
        mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].children[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].children[0].version").value(0));
    }

    /**
     * 17. publicTreeShowsOnlyUsableBranches: dựng hai nhánh; ngừng một danh mục cấp hai của nhánh 1 ->
     * GET /api/categories không còn nút đó cùng nhánh con của nó; nhánh 2 còn nguyên; thứ tự theo sortOrder và lồng ba cấp.
     */
    @Test
    void publicTreeShowsOnlyUsableBranches() throws Exception {
        // Nhánh 1
        UUID r1 = createCategory("Nhánh 1", null);
        UUID sub1A = createCategory("Nhánh 1 Cấp 2A", r1);
        createCategory("Nhánh 1 Cấp 3A", sub1A);
        UUID sub1B = createCategory("Nhánh 1 Cấp 2B", r1);
        UUID leaf1B = createCategory("Nhánh 1 Cấp 3B", sub1B);

        // Nhánh 2
        UUID r2 = createCategory("Nhánh 2", null);
        UUID sub2 = createCategory("Nhánh 2 Cấp 2", r2);
        UUID leaf2 = createCategory("Nhánh 2 Cấp 3", sub2);

        // Ngừng danh mục cấp 2 sub1A
        mockMvc.perform(post("/api/admin/categories/" + sub1A + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk());

        // GET /api/categories
        mockMvc.perform(get("/api/categories").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(r1.toString()))
                .andExpect(jsonPath("$.data[0].children.length()").value(1))
                .andExpect(jsonPath("$.data[0].children[0].id").value(sub1B.toString()))
                .andExpect(jsonPath("$.data[0].children[0].children[0].id").value(leaf1B.toString()))
                .andExpect(jsonPath("$.data[1].id").value(r2.toString()))
                .andExpect(jsonPath("$.data[1].children[0].id").value(sub2.toString()))
                .andExpect(jsonPath("$.data[1].children[0].children[0].id").value(leaf2.toString()));
    }

    /**
     * 18. adminTreeShowsEverythingWithUsableFlag: ngừng cấp một -> GET admin tree:
     * cấp một status=INACTIVE usable=false; con cấp hai status=ACTIVE nhưng usable=false; cháu usable=false; sortOrder và version có mặt.
     */
    @Test
    void adminTreeShowsEverythingWithUsableFlag() throws Exception {
        UUID r = createCategory("Cấp 1", null);
        UUID c = createCategory("Cấp 2", r);
        UUID g = createCategory("Cấp 3", c);

        // Ngừng cấp 1
        mockMvc.perform(post("/api/admin/categories/" + r + "/deactivate")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(r.toString()))
                .andExpect(jsonPath("$.data[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[0].children[0].id").value(c.toString()))
                .andExpect(jsonPath("$.data[0].children[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].children[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].children[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].children[0].version").value(0))
                .andExpect(jsonPath("$.data[0].children[0].children[0].id").value(g.toString()))
                .andExpect(jsonPath("$.data[0].children[0].children[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].children[0].children[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].children[0].children[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].children[0].children[0].version").value(0));
    }

    /**
     * 19. reorderSiblings: A, B, C cấp một; PUT reorder [C, A, B] (parentId bỏ qua) -> 200, `$.data` theo thứ tự C, A, B với sortOrder 0, 1, 2;
     * chỉ danh mục đổi chỗ có version=1, cái không đổi chỗ version vẫn 0; GET public tree theo đúng thứ tự mới. Reorder dưới một cha (parentId đặt) cũng chạy.
     */
    @Test
    void reorderSiblings() throws Exception {
        UUID a = createCategory("A", null);
        UUID b = createCategory("B", null);
        UUID c = createCategory("C", null);

        // Reorder cấp 1: C, A, B
        mockMvc.perform(put("/api/admin/categories/reorder")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "categoryIds": ["%s", "%s", "%s"]
                                }
                                """.formatted(c, a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(c.toString()))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[1].id").value(a.toString()))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data[1].version").value(1))
                .andExpect(jsonPath("$.data[2].id").value(b.toString()))
                .andExpect(jsonPath("$.data[2].sortOrder").value(2))
                .andExpect(jsonPath("$.data[2].version").value(1));

        // Kiểm tra GET /api/categories
        mockMvc.perform(get("/api/categories").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(c.toString()))
                .andExpect(jsonPath("$.data[1].id").value(a.toString()))
                .andExpect(jsonPath("$.data[2].id").value(b.toString()));

        // Thử reorder dưới một cha
        UUID parent = createCategory("Parent", null);
        UUID c1 = createCategory("Con 1", parent);
        UUID c2 = createCategory("Con 2", parent);

        mockMvc.perform(put("/api/admin/categories/reorder")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "parentId": "%s",
                                    "categoryIds": ["%s", "%s"]
                                }
                                """.formatted(parent, c2, c1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(c2.toString()))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].version").value(1))
                .andExpect(jsonPath("$.data[1].id").value(c1.toString()))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data[1].version").value(1));
    }

    /**
     * 20. reorderRejectsMissingExtraOrRepeated: thiếu một anh em, thừa một id (của cha khác), lặp id -> 409 CATEGORY_INVALID_ORDER;
     * thứ tự trong cơ sở dữ liệu không đổi, outbox_events không đổi; parentId là UUID ngẫu nhiên -> 404 CATEGORY_NOT_FOUND.
     */
    @Test
    void reorderRejectsMissingExtraOrRepeated() throws Exception {
        UUID a = createCategory("A", null);
        UUID b = createCategory("B", null);
        UUID c = createCategory("C", null);

        UUID parentOther = createCategory("Other", null);
        UUID otherChild = createCategory("Child Other", parentOther);

        int outboxBefore = countTotalOutbox();

        // Thiếu một anh em [A, B]
        mockMvc.perform(put("/api/admin/categories/reorder")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "categoryIds": ["%s", "%s"]
                                }
                                """.formatted(a, b)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID_ORDER"));

        // Thừa một id của cha khác [A, B, C, otherChild]
        mockMvc.perform(put("/api/admin/categories/reorder")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "categoryIds": ["%s", "%s", "%s", "%s"]
                                }
                                """.formatted(a, b, c, otherChild)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID_ORDER"));

        // Lặp id [A, B, B]
        mockMvc.perform(put("/api/admin/categories/reorder")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "categoryIds": ["%s", "%s", "%s"]
                                }
                                """.formatted(a, b, b)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID_ORDER"));

        assertThat(countTotalOutbox()).isEqualTo(outboxBefore);

        // Thứ tự không đổi
        mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(a.toString()))
                .andExpect(jsonPath("$.data[1].id").value(b.toString()))
                .andExpect(jsonPath("$.data[2].id").value(c.toString()));

        // parentId ngẫu nhiên -> 404 CATEGORY_NOT_FOUND
        UUID randomParent = UUID.randomUUID();
        mockMvc.perform(put("/api/admin/categories/reorder")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "parentId": "%s",
                                    "categoryIds": []
                                }
                                """.formatted(randomParent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    /**
     * 21. deleteChildlessCategory: xóa danh mục không con -> 200; bảng category giảm 1;
     * outbox_events thêm 1 hàng kiểm toán chứa CATEGORY_DELETED và 1 hàng catalog.category.changed; GET public tree không còn nó.
     */
    @Test
    void deleteChildlessCategory() throws Exception {
        UUID id = createCategory("Xóa Tôi Đi", null);

        int rowsBefore = countCategoryRows();
        int auditBefore = countAuditWithPayload("CATEGORY_DELETED");
        int changedBefore = countOutbox("catalog.category.changed");

        mockMvc.perform(delete("/api/admin/categories/" + id)
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(countCategoryRows()).isEqualTo(rowsBefore - 1);
        assertThat(countAuditWithPayload("CATEGORY_DELETED")).isEqualTo(auditBefore + 1);
        assertThat(countOutbox("catalog.category.changed")).isEqualTo(changedBefore + 1);

        // Public tree không còn
        mockMvc.perform(get("/api/categories").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 22. deleteWithChildrenIsConflict: xóa cha còn con -> 409 CATEGORY_HAS_CHILDREN; bảng category không đổi; outbox_events không đổi.
     */
    @Test
    void deleteWithChildrenIsConflict() throws Exception {
        UUID parent = createCategory("Cha", null);
        createCategory("Con", parent);

        int rowsBefore = countCategoryRows();
        int outboxBefore = countTotalOutbox();

        mockMvc.perform(delete("/api/admin/categories/" + parent)
                        .with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_CHILDREN"));

        assertThat(countCategoryRows()).isEqualTo(rowsBefore);
        assertThat(countTotalOutbox()).isEqualTo(outboxBefore);
    }

    /**
     * 23. replaceAssignmentsOnLeaf: mẫu "color" (SINGLE_SELECT có tùy chọn "red") và "weight" (NUMBER);
     * PUT attributes với hai mục (weight required=false displayOrder=1, color required=true displayOrder=0) -> 200,
     * `$.data` sắp theo displayOrder: color trước, weight sau, mỗi phần tử có usable=true, templateStatus=ACTIVE, required đúng;
     * version của lá = 1; kiểm toán chứa CATEGORY_UPDATED; GET assignments trả đủ;
     * GET /api/categories/{id}/attributes (người dùng thường) trả color kèm options [red] và weight, đúng thứ tự.
     */
    @Test
    void replaceAssignmentsOnLeaf() throws Exception {
        UUID leaf = buildThreeLevelBranch("Ngành Hàng");

        UUID colorTemplate = createTemplate("color", "Màu sắc", "SINGLE_SELECT");
        addOption(colorTemplate, "red", "Đỏ");

        UUID weightTemplate = createTemplate("weight", "Khối lượng", "NUMBER");

        int auditBefore = countAuditWithPayload("CATEGORY_UPDATED");

        String items = """
                [
                    {
                        "templateId": "%s",
                        "required": false,
                        "displayOrder": 1,
                        "constraints": null
                    },
                    {
                        "templateId": "%s",
                        "required": true,
                        "displayOrder": 0,
                        "constraints": null
                    }
                ]
                """.formatted(weightTemplate, colorTemplate);

        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": %s }
                                """.formatted(items)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].templateId").value(colorTemplate.toString()))
                .andExpect(jsonPath("$.data[0].required").value(true))
                .andExpect(jsonPath("$.data[0].displayOrder").value(0))
                .andExpect(jsonPath("$.data[0].usable").value(true))
                .andExpect(jsonPath("$.data[0].templateStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].templateId").value(weightTemplate.toString()))
                .andExpect(jsonPath("$.data[1].required").value(false))
                .andExpect(jsonPath("$.data[1].displayOrder").value(1))
                .andExpect(jsonPath("$.data[1].usable").value(true))
                .andExpect(jsonPath("$.data[1].templateStatus").value("ACTIVE"));

        assertThat(countAuditWithPayload("CATEGORY_UPDATED")).isEqualTo(auditBefore + 1);

        // Kiểm tra version của lá = 1 qua admin tree
        mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children[0].children[0].id").value(leaf.toString()))
                .andExpect(jsonPath("$.data[0].children[0].children[0].version").value(1));

        // GET assignments (admin)
        mockMvc.perform(get("/api/admin/categories/" + leaf + "/assignments").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].templateId").value(colorTemplate.toString()))
                .andExpect(jsonPath("$.data[1].templateId").value(weightTemplate.toString()));

        // GET /api/categories/{id}/attributes (user)
        mockMvc.perform(get("/api/categories/" + leaf + "/attributes").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].templateId").value(colorTemplate.toString()))
                .andExpect(jsonPath("$.data[0].options[0].value").value("red"))
                .andExpect(jsonPath("$.data[0].options[0].displayValue").value("Đỏ"))
                .andExpect(jsonPath("$.data[1].templateId").value(weightTemplate.toString()));
    }

    /**
     * 24. replaceIsAReplacementNotAnAddition: gán [color, weight], rồi gán [weight] -> chỉ còn weight; gán [] -> rỗng.
     */
    @Test
    void replaceIsAReplacementNotAnAddition() throws Exception {
        UUID leaf = buildThreeLevelBranch("Thời trang");
        UUID color = createTemplate("color", "Màu sắc", "SINGLE_SELECT");
        addOption(color, "blue", "Xanh");
        UUID weight = createTemplate("weight", "Cân nặng", "NUMBER");

        // Gán [color, weight]
        replaceAssignments(leaf, """
                [
                    { "templateId": "%s", "required": false, "displayOrder": 0 },
                    { "templateId": "%s", "required": false, "displayOrder": 1 }
                ]
                """.formatted(color, weight));

        // Gán chỉ [weight]
        replaceAssignments(leaf, """
                [
                    { "templateId": "%s", "required": false, "displayOrder": 0 }
                ]
                """.formatted(weight));

        mockMvc.perform(get("/api/admin/categories/" + leaf + "/assignments").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].templateId").value(weight.toString()));

        // Gán []
        replaceAssignments(leaf, "[]");

        mockMvc.perform(get("/api/admin/categories/" + leaf + "/assignments").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 25. replaceOnNonLeafIsConflict: cấp một hoặc hai -> 409 CATEGORY_NOT_LEAF; GET /api/categories/{id}/attributes của cấp một -> 200 và `$.data` rỗng.
     */
    @Test
    void replaceOnNonLeafIsConflict() throws Exception {
        UUID root = createCategory("Root", null);
        UUID sub = createCategory("Sub", root);
        createCategory("Leaf", sub);

        UUID t = createTemplate("weight", "Cân nặng", "NUMBER");

        // Gán trên root -> 409
        mockMvc.perform(put("/api/admin/categories/" + root + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [ { "templateId": "%s", "required": false, "displayOrder": 0 } ]
                                }
                                """.formatted(t)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_LEAF"));

        // Gán trên sub -> 409
        mockMvc.perform(put("/api/admin/categories/" + sub + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [ { "templateId": "%s", "required": false, "displayOrder": 0 } ]
                                }
                                """.formatted(t)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_LEAF"));

        // GET /api/categories/{id}/attributes của root -> 200 và data rỗng
        mockMvc.perform(get("/api/categories/" + root + "/attributes").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 26. replaceRejectsUnknownDuplicateAndUnusableTemplates: templateId ngẫu nhiên -> 404 ATTRIBUTE_TEMPLATE_NOT_FOUND;
     * cùng một mẫu hai lần -> 409 ATTRIBUTE_ASSIGNMENT_DUPLICATED;
     * mẫu đã ngừng dùng (mới thêm) -> 409 ATTRIBUTE_TEMPLATE_INACTIVE;
     * mẫu SINGLE_SELECT chưa có tùy chọn nào -> 409 ATTRIBUTE_TEMPLATE_INACTIVE;
     * sau mỗi lỗi danh sách gán và version của lá không đổi; thiếu `items` -> 400.
     */
    @Test
    void replaceRejectsUnknownDuplicateAndUnusableTemplates() throws Exception {
        UUID leaf = buildThreeLevelBranch("Đồ chơi");
        UUID validTemplate = createTemplate("weight", "Cân nặng", "NUMBER");
        replaceAssignments(leaf, """
                [ { "templateId": "%s", "required": false, "displayOrder": 0 } ]
                """.formatted(validTemplate));

        UUID inactiveTemplate = createTemplate("brand_old", "Thương hiệu cũ", "TEXT");
        deactivateTemplate(inactiveTemplate);

        UUID singleWithoutOptions = createTemplate("material", "Chất liệu", "SINGLE_SELECT");

        // templateId ngẫu nhiên -> 404 ATTRIBUTE_TEMPLATE_NOT_FOUND
        UUID randomTemplateId = UUID.randomUUID();
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [ { "templateId": "%s", "required": false, "displayOrder": 0 } ]
                                }
                                """.formatted(randomTemplateId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_NOT_FOUND"));

        // Trùng mẫu -> 409 ATTRIBUTE_ASSIGNMENT_DUPLICATED
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0 },
                                        { "templateId": "%s", "required": true, "displayOrder": 1 }
                                    ]
                                }
                                """.formatted(validTemplate, validTemplate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_ASSIGNMENT_DUPLICATED"));

        // Mẫu đã ngừng dùng -> 409 ATTRIBUTE_TEMPLATE_INACTIVE
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [ { "templateId": "%s", "required": false, "displayOrder": 0 } ]
                                }
                                """.formatted(inactiveTemplate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_INACTIVE"));

        // SINGLE_SELECT chưa có tùy chọn -> 409 ATTRIBUTE_TEMPLATE_INACTIVE
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [ { "templateId": "%s", "required": false, "displayOrder": 0 } ]
                                }
                                """.formatted(singleWithoutOptions)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_INACTIVE"));

        // Thiếu field items -> 400
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest());

        // Kiểm tra danh sách gán và version của lá vẫn giữ nguyên
        mockMvc.perform(get("/api/admin/categories/" + leaf + "/assignments").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].templateId").value(validTemplate.toString()));

        mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].children[0].children[0].version").value(1));
    }

    /**
     * 27. alreadyAssignedTemplateStaysAfterItWasDeactivated: gán mẫu M, ngừng M qua API mẫu;
     * PUT attributes giữ M và đổi required -> 200 (mẫu cũ được giữ); phần tử có usable=false, templateStatus=INACTIVE;
     * GET assignments (admin) có M; GET /api/categories/{id}/attributes (công khai) KHÔNG có M.
     */
    @Test
    void alreadyAssignedTemplateStaysAfterItWasDeactivated() throws Exception {
        UUID leaf = buildThreeLevelBranch("Điện thoại");
        UUID m = createTemplate("screen_size", "Kích thước màn hình", "NUMBER");

        // Gán mẫu M
        replaceAssignments(leaf, """
                [ { "templateId": "%s", "required": false, "displayOrder": 0 } ]
                """.formatted(m));

        // Ngừng M qua API mẫu
        deactivateTemplate(m);

        // PUT attributes giữ M và đổi required thành true -> 200
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [ { "templateId": "%s", "required": true, "displayOrder": 0 } ]
                                }
                                """.formatted(m)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].templateId").value(m.toString()))
                .andExpect(jsonPath("$.data[0].required").value(true))
                .andExpect(jsonPath("$.data[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].templateStatus").value("INACTIVE"));

        // GET assignments (admin) có M
        mockMvc.perform(get("/api/admin/categories/" + leaf + "/assignments").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].templateId").value(m.toString()))
                .andExpect(jsonPath("$.data[0].usable").value(false))
                .andExpect(jsonPath("$.data[0].templateStatus").value("INACTIVE"));

        // GET /api/categories/{id}/attributes (công khai) KHÔNG có M
        mockMvc.perform(get("/api/categories/" + leaf + "/attributes").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    /**
     * 28. constraintsAreValidatedByInputType: TEXT với {"maxLength":50} -> 200 và `constraints.maxLength`=50 trong phản hồi;
     * NUMBER với {"min":1,"max":10,"integerOnly":true} -> 200;
     * NUMBER với {"min":10,"max":1} -> 400 ATTRIBUTE_CONSTRAINTS_INVALID;
     * TEXT với {"maxLength":0} và {"maxLength":256} -> 400;
     * NUMBER với {"maxLength":10} -> 400;
     * MULTI_SELECT (có tùy chọn) với {"maxSelections":0} -> 400, với {"maxSelections":3} -> 200;
     * SINGLE_SELECT với {"maxLength":10} -> 400;
     * sau mỗi lỗi danh sách gán không đổi.
     */
    @Test
    void constraintsAreValidatedByInputType() throws Exception {
        UUID leaf = buildThreeLevelBranch("Gia dụng");

        UUID textT = createTemplate("description", "Mô tả", "TEXT");
        UUID numT = createTemplate("quantity", "Số lượng", "NUMBER");
        UUID multiT = createTemplate("tags", "Thẻ", "MULTI_SELECT");
        addOption(multiT, "opt1", "Tùy chọn 1");
        addOption(multiT, "opt2", "Tùy chọn 2");
        addOption(multiT, "opt3", "Tùy chọn 3");
        UUID singleT = createTemplate("mode", "Chế độ", "SINGLE_SELECT");
        addOption(singleT, "auto", "Tự động");

        // 1. TEXT với maxLength=50 -> 200
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "maxLength": 50 } }
                                    ]
                                }
                                """.formatted(textT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].constraints.maxLength").value(50));

        // 2. NUMBER với min=1, max=10, integerOnly=true -> 200
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "min": 1, "max": 10, "integerOnly": true } }
                                    ]
                                }
                                """.formatted(numT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].constraints.min").value(1))
                .andExpect(jsonPath("$.data[0].constraints.max").value(10))
                .andExpect(jsonPath("$.data[0].constraints.integerOnly").value(true));

        // 3. NUMBER với min=10, max=1 -> 400 ATTRIBUTE_CONSTRAINTS_INVALID
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "min": 10, "max": 1 } }
                                    ]
                                }
                                """.formatted(numT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_CONSTRAINTS_INVALID"));

        // 4. TEXT với maxLength=0 -> 400
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "maxLength": 0 } }
                                    ]
                                }
                                """.formatted(textT)))
                .andExpect(status().isBadRequest());

        // 5. TEXT với maxLength=256 -> 400
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "maxLength": 256 } }
                                    ]
                                }
                                """.formatted(textT)))
                .andExpect(status().isBadRequest());

        // 6. NUMBER với maxLength=10 -> 400
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "maxLength": 10 } }
                                    ]
                                }
                                """.formatted(numT)))
                .andExpect(status().isBadRequest());

        // 7. MULTI_SELECT với maxSelections=0 -> 400
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "maxSelections": 0 } }
                                    ]
                                }
                                """.formatted(multiT)))
                .andExpect(status().isBadRequest());

        // 8. MULTI_SELECT với maxSelections=3 -> 200
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "maxSelections": 3 } }
                                    ]
                                }
                                """.formatted(multiT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].constraints.maxSelections").value(3));

        // 9. SINGLE_SELECT với maxLength=10 -> 400
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "items": [
                                        { "templateId": "%s", "required": false, "displayOrder": 0, "constraints": { "maxLength": 10 } }
                                    ]
                                }
                                """.formatted(singleT)))
                .andExpect(status().isBadRequest());

        // Danh sách gán không đổi sau lỗi (vẫn giữ MULTI_SELECT với maxSelections=3 từ bước 8)
        mockMvc.perform(get("/api/admin/categories/" + leaf + "/assignments").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].templateId").value(multiT.toString()))
                .andExpect(jsonPath("$.data[0].constraints.maxSelections").value(3));
    }

    /** api.yaml: required mặc định false, displayOrder mặc định 0; bỏ qua hai trường này không phải lỗi 400. */
    @Test
    void replaceAcceptsItemsWithoutRequiredAndDisplayOrder() throws Exception {
        UUID leaf = buildThreeLevelBranch("Mac dinh");
        UUID template = createTemplate("note", "Ghi chú", "TEXT");

        MvcResult replaced = replaceAssignments(leaf, """
                [{"templateId":"%s"}]
                """.formatted(template));
        org.assertj.core.api.Assertions.assertThat(replaced.getResponse().getStatus()).isEqualTo(200);

        mockMvc.perform(get("/api/admin/categories/{id}/assignments", leaf).with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].required").value(false))
                .andExpect(jsonPath("$.data[0].displayOrder").value(0));
    }
}
