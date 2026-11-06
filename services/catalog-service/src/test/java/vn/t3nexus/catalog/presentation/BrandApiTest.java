package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử toàn diện API quản lý thương hiệu (BrandApiTest).
 */
class BrandApiTest extends CatalogApiTestBase {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /**
     * 1. unauthenticatedIsRejected: mỗi đường không token -> 401.
     */
    @Test
    void unauthenticatedIsRejected() throws Exception {
        UUID randomId = UUID.randomUUID();

        // GET /api/brands
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isUnauthorized());

        // GET /api/admin/brands
        mockMvc.perform(get("/api/admin/brands"))
                .andExpect(status().isUnauthorized());

        // GET /api/admin/brands/{id}
        mockMvc.perform(get("/api/admin/brands/{id}", randomId))
                .andExpect(status().isUnauthorized());

        // POST /api/admin/brands
        mockMvc.perform(post("/api/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isUnauthorized());

        // PUT /api/admin/brands/{id}
        mockMvc.perform(put("/api/admin/brands/{id}", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple Inc"}
                                """))
                .andExpect(status().isUnauthorized());

        // POST /api/admin/brands/{id}/deactivate
        mockMvc.perform(post("/api/admin/brands/{id}/deactivate", randomId))
                .andExpect(status().isUnauthorized());

        // POST /api/admin/brands/{id}/activate
        mockMvc.perform(post("/api/admin/brands/{id}/activate", randomId))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 2. nonAdminCannotUseAdminEndpoints: token USER gọi 6 đường admin -> 403 CATALOG_ROLE_NOT_ALLOWED;
     * chưa có thương hiệu nào được tạo (đếm bảng brand = 0 sau ca post).
     */
    @Test
    void nonAdminCannotUseAdminEndpoints() throws Exception {
        UUID randomId = UUID.randomUUID();

        // 1. GET /api/admin/brands
        mockMvc.perform(get("/api/admin/brands").with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // 2. GET /api/admin/brands/{id}
        mockMvc.perform(get("/api/admin/brands/{id}", randomId).with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // 3. POST /api/admin/brands
        mockMvc.perform(post("/api/admin/brands")
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // 4. PUT /api/admin/brands/{id}
        mockMvc.perform(put("/api/admin/brands/{id}", randomId)
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple Inc"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // 5. POST /api/admin/brands/{id}/deactivate
        mockMvc.perform(post("/api/admin/brands/{id}/deactivate", randomId).with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // 6. POST /api/admin/brands/{id}/activate
        mockMvc.perform(post("/api/admin/brands/{id}/activate", randomId).with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // Kiểm tra chưa có thương hiệu nào được tạo trong DB
        Integer brandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Integer.class);
        assertThat(brandCount).isZero();
    }

    /**
     * 3. createReturns201WithVersionZero: ADMIN POST {"name":"Apple"} -> 201, name="Apple", status="ACTIVE", version=0,
     * id là UUID; bảng brand có đúng 1 hàng; outbox_events có đúng 1 hàng, routing_key audit.action.recorded, payload chứa BRAND_CREATED.
     */
    @Test
    void createReturns201WithVersionZero() throws Exception {
        var result = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Apple"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();

        // Kiểm tra id là chuỗi UUID hợp lệ
        String idStr = json(result).path("data").path("id").asText();
        assertThat(UUID.fromString(idStr)).isNotNull();

        // Kiểm tra bảng brand có đúng 1 hàng
        Integer brandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Integer.class);
        assertThat(brandCount).isEqualTo(1);

        // Kiểm tra outbox_events có đúng 1 hàng
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ?", Integer.class, "audit.action.recorded");
        assertThat(outboxCount).isEqualTo(1);

        Integer payloadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ? AND payload LIKE '%BRAND_CREATED%'",
                Integer.class, "audit.action.recorded");
        assertThat(payloadCount).isEqualTo(1);
    }

    /**
     * 4. createRejectsBlankAndTooLongName: name rỗng, toàn dấu cách, 101 ký tự -> 400 CATALOG_FIELD_INVALID;
     * không có hàng nào được thêm (brand và outbox_events).
     */
    @Test
    void createRejectsBlankAndTooLongName() throws Exception {
        int initialBrandCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Integer.class);
        int initialOutboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);

        // Tên rỗng
        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        // Tên toàn dấu cách
        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        // Tên 101 ký tự
        String longName = "A".repeat(101);
        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s"}
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        // Không có hàng nào được thêm
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Integer.class)).isEqualTo(initialBrandCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(initialOutboxCount);
    }

    /**
     * 5. createRejectsDuplicateNameAfterNormalization: tạo "Điện Quang"; sau đó POST "DIEN   quang" -> 409 BRAND_NAME_TAKEN;
     * bảng brand vẫn 1 hàng; outbox_events vẫn 1 hàng.
     */
    @Test
    void createRejectsDuplicateNameAfterNormalization() throws Exception {
        // Tạo "Điện Quang" thành công
        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Điện Quang"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        int brandCountAfterCreate = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Integer.class);
        int outboxCountAfterCreate = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
        assertThat(brandCountAfterCreate).isEqualTo(1);
        assertThat(outboxCountAfterCreate).isEqualTo(1);

        // Tạo lại với tên đã chuẩn hóa giống nhau -> 409 BRAND_NAME_TAKEN
        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"DIEN   quang"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRAND_NAME_TAKEN"));

        // Bảng brand và outbox_events vẫn giữ nguyên 1 hàng
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM brand", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(1);
    }

    /**
     * 6. renameChangesNameAndBumpsVersion: PUT đổi "Apple" thành "Apple Inc" -> 200, name đổi, version=1;
     * GET admin chi tiết cùng kết quả; outbox_events thêm 1 hàng kiểm toán chứa BRAND_UPDATED.
     */
    @Test
    void renameChangesNameAndBumpsVersion() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String brandId = json(createResult).path("data").path("id").asText();

        // PUT đổi tên
        mockMvc.perform(put("/api/admin/brands/{id}", brandId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple Inc"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Apple Inc"))
                .andExpect(jsonPath("$.data.version").value(1));

        // GET admin chi tiết cùng kết quả
        mockMvc.perform(get("/api/admin/brands/{id}", brandId).with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Apple Inc"))
                .andExpect(jsonPath("$.data.version").value(1));

        // outbox_events thêm 1 hàng kiểm toán chứa BRAND_UPDATED (tổng là 2 hàng)
        Integer outboxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ?", Integer.class, "audit.action.recorded");
        assertThat(outboxCount).isEqualTo(2);

        Integer updatedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ? AND payload LIKE '%BRAND_UPDATED%'",
                Integer.class, "audit.action.recorded");
        assertThat(updatedCount).isEqualTo(1);
    }

    /**
     * 7. renameToAnotherBrandsNameIsConflictButOwnSpellingIsAllowed: có "Apple" và "Samsung";
     * đổi Apple thành "samsung" -> 409 BRAND_NAME_TAKEN; đổi Apple thành "APPLE" -> 200.
     */
    @Test
    void renameToAnotherBrandsNameIsConflictButOwnSpellingIsAllowed() throws Exception {
        var appleRes = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String appleId = json(appleRes).path("data").path("id").asText();

        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Samsung"}
                                """))
                .andExpect(status().isCreated());

        // Đổi Apple thành "samsung" -> 409 BRAND_NAME_TAKEN
        mockMvc.perform(put("/api/admin/brands/{id}", appleId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"samsung"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRAND_NAME_TAKEN"));

        // Đổi Apple thành "APPLE" -> 200
        mockMvc.perform(put("/api/admin/brands/{id}", appleId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"APPLE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("APPLE"));
    }

    /**
     * 8. renameUnknownIsNotFound: PUT với UUID ngẫu nhiên -> 404 BRAND_NOT_FOUND.
     * Cũng GET admin chi tiết, deactivate, activate với UUID ngẫu nhiên -> 404 BRAND_NOT_FOUND.
     */
    @Test
    void renameUnknownIsNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();

        // PUT
        mockMvc.perform(put("/api/admin/brands/{id}", unknownId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Whatever"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));

        // GET chi tiết
        mockMvc.perform(get("/api/admin/brands/{id}", unknownId).with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));

        // POST deactivate
        mockMvc.perform(post("/api/admin/brands/{id}/deactivate", unknownId).with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));

        // POST activate
        mockMvc.perform(post("/api/admin/brands/{id}/activate", unknownId).with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));
    }

    /**
     * 9. malformedIdIsBadRequest: GET /api/admin/brands/khong-phai-uuid -> 400, code = INVALID_PARAMETER.
     */
    @Test
    void malformedIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/brands/khong-phai-uuid").with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /**
     * 10. deactivateThenActivate: deactivate -> 200 status=INACTIVE version=1; deactivate lần nữa -> 409 CATALOG_INVALID_STATE
     * (version vẫn 1 khi GET lại, số hàng outbox_events không đổi); activate -> 200 status=ACTIVE version=2; activate lần nữa -> 409 CATALOG_INVALID_STATE.
     * Kiểm toán: deactivate ghi BRAND_DEACTIVATED; activate ghi BRAND_UPDATED.
     */
    @Test
    void deactivateThenActivate() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String brandId = json(createResult).path("data").path("id").asText();

        // 1. Deactivate lần 1 -> 200, status=INACTIVE, version=1
        mockMvc.perform(post("/api/admin/brands/{id}/deactivate", brandId).with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.version").value(1));

        // Kiểm toán deactivate ghi BRAND_DEACTIVATED
        Integer deactCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ? AND payload LIKE '%BRAND_DEACTIVATED%'",
                Integer.class, "audit.action.recorded");
        assertThat(deactCount).isEqualTo(1);

        int outboxBeforeSecondDeactivate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events", Integer.class);

        // 2. Deactivate lần 2 -> 409 CATALOG_INVALID_STATE
        mockMvc.perform(post("/api/admin/brands/{id}/deactivate", brandId).with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));

        // version vẫn 1 khi GET lại
        mockMvc.perform(get("/api/admin/brands/{id}", brandId).with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // Số hàng outbox_events không đổi
        int outboxAfterSecondDeactivate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events", Integer.class);
        assertThat(outboxAfterSecondDeactivate).isEqualTo(outboxBeforeSecondDeactivate);

        // 3. Activate lần 1 -> 200, status=ACTIVE, version=2
        mockMvc.perform(post("/api/admin/brands/{id}/activate", brandId).with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2));

        // Kiểm toán activate ghi BRAND_UPDATED
        Integer actUpdatedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ? AND payload LIKE '%BRAND_UPDATED%'",
                Integer.class, "audit.action.recorded");
        assertThat(actUpdatedCount).isEqualTo(1);

        // 4. Activate lần 2 -> 409 CATALOG_INVALID_STATE
        mockMvc.perform(post("/api/admin/brands/{id}/activate", brandId).with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));
    }

    /**
     * 11. renameAllowedWhileInactive: deactivate rồi PUT đổi tên -> 200.
     */
    @Test
    void renameAllowedWhileInactive() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String brandId = json(createResult).path("data").path("id").asText();

        // Deactivate
        mockMvc.perform(post("/api/admin/brands/{id}/deactivate", brandId).with(admin(ADMIN)))
                .andExpect(status().isOk());

        // PUT đổi tên khi đang INACTIVE -> 200
        mockMvc.perform(put("/api/admin/brands/{id}", brandId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple Corporation"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Apple Corporation"))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    /**
     * 12. publicListShowsOnlyActiveAndSearchesIgnoringCaseAndAccents: tạo "Điện Quang", "Samsung", "Apple";
     * deactivate Apple; USER GET /api/brands -> total=2, không có Apple;
     * ?keyword=DIEN -> chỉ "Điện Quang"; ?keyword=%20sung%20 -> chỉ Samsung;
     * ADMIN GET /api/admin/brands -> total=3; ?status=INACTIVE -> chỉ Apple.
     */
    @Test
    void publicListShowsOnlyActiveAndSearchesIgnoringCaseAndAccents() throws Exception {
        // Tạo "Điện Quang"
        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Điện Quang"}
                                """))
                .andExpect(status().isCreated());

        // Tạo "Samsung"
        mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Samsung"}
                                """))
                .andExpect(status().isCreated());

        // Tạo "Apple"
        var appleRes = mockMvc.perform(post("/api/admin/brands")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Apple"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String appleId = json(appleRes).path("data").path("id").asText();

        // Deactivate Apple
        mockMvc.perform(post("/api/admin/brands/{id}/deactivate", appleId).with(admin(ADMIN)))
                .andExpect(status().isOk());

        // USER GET /api/brands -> total=2, không có Apple
        mockMvc.perform(get("/api/brands").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[?(@.name == 'Apple')]").doesNotExist());

        // ?keyword=DIEN -> chỉ "Điện Quang"
        mockMvc.perform(get("/api/brands").param("keyword", "DIEN").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Điện Quang"));

        // ?keyword=%20sung%20 -> chỉ Samsung
        mockMvc.perform(get("/api/brands").param("keyword", " sung ").with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Samsung"));

        // ADMIN GET /api/admin/brands -> total=3
        mockMvc.perform(get("/api/admin/brands").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3));

        // ?status=INACTIVE -> chỉ Apple
        mockMvc.perform(get("/api/admin/brands").param("status", "INACTIVE").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Apple"))
                .andExpect(jsonPath("$.data.items[0].status").value("INACTIVE"));
    }

    /**
     * 13. paginationWorks: tạo 3 thương hiệu; GET /api/brands?page=0&size=2 -> items 2 phần tử, total=3;
     * page=1&size=2 -> 1 phần tử.
     */
    @Test
    void paginationWorks() throws Exception {
        for (String name : new String[]{"Brand A", "Brand B", "Brand C"}) {
            mockMvc.perform(post("/api/admin/brands")
                            .with(admin(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"%s"}
                                    """.formatted(name)))
                    .andExpect(status().isCreated());
        }

        // Trang 0, kích thước 2
        mockMvc.perform(get("/api/brands")
                        .param("page", "0")
                        .param("size", "2")
                        .with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        // Trang 1, kích thước 2
        mockMvc.perform(get("/api/brands")
                        .param("page", "1")
                        .param("size", "2")
                        .with(user(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }
}