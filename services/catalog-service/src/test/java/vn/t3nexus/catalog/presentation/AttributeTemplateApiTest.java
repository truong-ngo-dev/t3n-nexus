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
 * Kiểm thử toàn diện API mẫu thuộc tính và tùy chọn (AttributeTemplateApiTest).
 */
class AttributeTemplateApiTest extends CatalogApiTestBase {

    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /**
     * 1. unauthenticatedIsRejected: GET list, GET detail, POST create, POST options không token -> 401.
     */
    @Test
    void unauthenticatedIsRejected() throws Exception {
        UUID randomId = UUID.randomUUID();

        // GET list
        mockMvc.perform(get("/api/admin/attribute-templates"))
                .andExpect(status().isUnauthorized());

        // GET detail
        mockMvc.perform(get("/api/admin/attribute-templates/{id}", randomId))
                .andExpect(status().isUnauthorized());

        // POST create
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        // POST options
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", randomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 2. nonAdminCannotUseAnyEndpoint: token USER gọi GET list, GET detail, POST create, PUT update,
     * deactivate, POST options -> 403 CATALOG_ROLE_NOT_ALLOWED; bảng attribute_template vẫn rỗng.
     */
    @Test
    void nonAdminCannotUseAnyEndpoint() throws Exception {
        UUID randomId = UUID.randomUUID();

        // GET list
        mockMvc.perform(get("/api/admin/attribute-templates").with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // GET detail
        mockMvc.perform(get("/api/admin/attribute-templates/{id}", randomId).with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // POST create
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // PUT update
        mockMvc.perform(put("/api/admin/attribute-templates/{id}", randomId)
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Màu sắc mới",
                                  "hint": "Gợi ý"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // POST deactivate
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/deactivate", randomId).with(user(USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // POST options
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", randomId)
                        .with(user(USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));

        // Bảng attribute_template vẫn rỗng
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attribute_template", Integer.class);
        assertThat(count).isZero();
    }

    /**
     * 3. createReturns201WithVersionZero: ADMIN tạo {"name":"color","displayName":"Màu sắc","inputType":"SINGLE_SELECT"}
     * -> 201, name="color", status="ACTIVE", version=0, options rỗng, usable=false (kiểu chọn chưa có tùy chọn);
     * outbox_events có 2 hàng: 1 routing_key audit.action.recorded (payload chứa ATTRIBUTE_CREATED) và
     * 1 routing_key catalog.attribute-template.changed.
     */
    @Test
    void createReturns201WithVersionZero() throws Exception {
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("color"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.options").isEmpty());

        // Kiểm tra outbox_events có 2 hàng
        Integer totalOutbox = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
        assertThat(totalOutbox).isEqualTo(2);

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ? AND payload LIKE '%ATTRIBUTE_CREATED%'",
                Integer.class, "audit.action.recorded");
        assertThat(auditCount).isEqualTo(1);

        Integer changeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ?",
                Integer.class, "catalog.attribute-template.changed");
        assertThat(changeCount).isEqualTo(1);
    }

    /**
     * 4. createNumberWithUnitAndTextWithoutOption: tạo NUMBER có "unit":"kg" -> 201, unit="kg", usable=true;
     * tạo TEXT -> 201, usable=true.
     */
    @Test
    void createNumberWithUnitAndTextWithoutOption() throws Exception {
        // Tạo NUMBER có unit: "kg"
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "weight",
                                  "displayName": "Khối lượng",
                                  "inputType": "NUMBER",
                                  "unit": "kg"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unit").value("kg"))
                .andExpect(jsonPath("$.data.usable").value(true));

        // Tạo TEXT
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "description",
                                  "displayName": "Mô tả",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usable").value(true));
    }

    /**
     * 5. createRejectsNameOutsideCodeFormat: name "Color", "màu sắc", "mau-sac", "_color", "color_", "a__b", "color "
     * -> 400 CATALOG_FIELD_INVALID; không hàng nào được thêm (attribute_template, outbox_events).
     */
    @Test
    void createRejectsNameOutsideCodeFormat() throws Exception {
        String[] invalidNames = {"Color", "màu sắc", "mau-sac", "_color", "color_", "a__b", "color "};

        int initialTemplateCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attribute_template", Integer.class);
        int initialOutboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);

        for (String invalidName : invalidNames) {
            mockMvc.perform(post("/api/admin/attribute-templates")
                            .with(admin(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "%s",
                                      "displayName": "Kiểm tra",
                                      "inputType": "TEXT"
                                    }
                                    """.formatted(invalidName)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attribute_template", Integer.class))
                .isEqualTo(initialTemplateCount);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class))
                .isEqualTo(initialOutboxCount);
    }

    /**
     * 6. createRejectsBlankDisplayNameAndUnitOnNonNumber: displayName rỗng -> 400 CATALOG_FIELD_INVALID;
     * "unit":"kg" cho kiểu TEXT -> 400 CATALOG_FIELD_INVALID; thiếu inputType -> 400.
     */
    @Test
    void createRejectsBlankDisplayNameAndUnitOnNonNumber() throws Exception {
        // displayName rỗng -> 400 CATALOG_FIELD_INVALID
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        // "unit":"kg" cho kiểu TEXT -> 400 CATALOG_FIELD_INVALID
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "TEXT",
                                  "unit": "kg"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));

        // thiếu inputType -> 400
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 7. createRejectsDuplicateName: tạo "color"; tạo lại "color" (displayName khác) -> 409 ATTRIBUTE_TEMPLATE_NAME_TAKEN;
     * attribute_template vẫn 1 hàng; outbox_events không đổi sau lần lỗi.
     */
    @Test
    void createRejectsDuplicateName() throws Exception {
        // Tạo "color" lần 1 thành công
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        int templateCountAfterCreate = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attribute_template", Integer.class);
        int outboxCountAfterCreate = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);
        assertThat(templateCountAfterCreate).isEqualTo(1);

        // Tạo lại "color" với displayName khác -> 409 ATTRIBUTE_TEMPLATE_NAME_TAKEN
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc khác",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_NAME_TAKEN"));

        // attribute_template vẫn 1 hàng; outbox_events không đổi
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attribute_template", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class))
                .isEqualTo(outboxCountAfterCreate);
    }

    /**
     * 8. updateChangesOnlyDisplayNameAndHint: PUT {"displayName":"Màu","hint":"Chọn màu"} -> 200, displayName/hint đổi,
     * name giữ nguyên, version=1; kiểu nhập và đơn vị không đổi; kiểm toán có ATTRIBUTE_UPDATED.
     */
    @Test
    void updateChangesOnlyDisplayNameAndHint() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "hint": "Gợi ý cũ",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // PUT cập nhật displayName và hint
        mockMvc.perform(put("/api/admin/attribute-templates/{id}", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Màu",
                                  "hint": "Chọn màu"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("color"))
                .andExpect(jsonPath("$.data.displayName").value("Màu"))
                .andExpect(jsonPath("$.data.hint").value("Chọn màu"))
                .andExpect(jsonPath("$.data.inputType").value("TEXT"))
                .andExpect(jsonPath("$.data.version").value(1));

        // Kiểm toán có ATTRIBUTE_UPDATED
        Integer updatedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ? AND payload LIKE '%ATTRIBUTE_UPDATED%'",
                Integer.class, "audit.action.recorded");
        assertThat(updatedCount).isEqualTo(1);
    }

    /**
     * 9. detailUnknownAndMalformed: GET/PUT/deactivate/POST options với UUID ngẫu nhiên -> 404 ATTRIBUTE_TEMPLATE_NOT_FOUND;
     * GET /khong-phai-uuid -> 400 INVALID_PARAMETER.
     */
    @Test
    void detailUnknownAndMalformed() throws Exception {
        UUID unknownId = UUID.randomUUID();

        // GET detail unknown
        mockMvc.perform(get("/api/admin/attribute-templates/{id}", unknownId).with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_NOT_FOUND"));

        // PUT update unknown
        mockMvc.perform(put("/api/admin/attribute-templates/{id}", unknownId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Màu",
                                  "hint": "Gợi ý"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_NOT_FOUND"));

        // POST deactivate unknown
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/deactivate", unknownId).with(admin(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_NOT_FOUND"));

        // POST options unknown
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", unknownId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_NOT_FOUND"));

        // GET malformed id
        mockMvc.perform(get("/api/admin/attribute-templates/khong-phai-uuid").with(admin(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    /**
     * 10. deactivateThenActivate: deactivate -> 200 status=INACTIVE usable=false version=1, kiểm toán ATTRIBUTE_DEACTIVATED;
     * deactivate lần nữa -> 409 CATALOG_INVALID_STATE (outbox_events không đổi); activate -> 200 status=ACTIVE version=2;
     * activate lần nữa -> 409 CATALOG_INVALID_STATE.
     */
    @Test
    void deactivateThenActivate() throws Exception {
        // Tạo mẫu TEXT (ban đầu usable=true)
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "description",
                                  "displayName": "Mô tả",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // 1. Deactivate lần 1 -> 200, status=INACTIVE, usable=false, version=1
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/deactivate", templateId).with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.version").value(1));

        // Kiểm toán ATTRIBUTE_DEACTIVATED
        Integer deactAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE routing_key = ? AND payload LIKE '%ATTRIBUTE_DEACTIVATED%'",
                Integer.class, "audit.action.recorded");
        assertThat(deactAudit).isEqualTo(1);

        int outboxBeforeSecondDeactivate = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class);

        // 2. Deactivate lần 2 -> 409 CATALOG_INVALID_STATE
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/deactivate", templateId).with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));

        // outbox_events không đổi
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class))
                .isEqualTo(outboxBeforeSecondDeactivate);

        // 3. Activate lần 1 -> 200, status=ACTIVE, version=2
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/activate", templateId).with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.usable").value(true))
                .andExpect(jsonPath("$.data.version").value(2));

        // 4. Activate lần 2 -> 409 CATALOG_INVALID_STATE
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/activate", templateId).with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));
    }

    /**
     * 11. addOptionReturns201AndMakesSelectTemplateUsable: mẫu SINGLE_SELECT; POST options {"value":"red","displayValue":"Đỏ"}
     * -> 201, options có 1 phần tử value="red" status=ACTIVE sortOrder=0, usable=true, version=1;
     * thêm "blue" -> sortOrder=1 (tùy chọn mới xếp cuối).
     */
    @Test
    void addOptionReturns201AndMakesSelectTemplateUsable() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // Thêm tùy chọn "red" -> sortOrder=0, usable=true, version=1
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usable").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.options.length()").value(1))
                .andExpect(jsonPath("$.data.options[0].value").value("red"))
                .andExpect(jsonPath("$.data.options[0].displayValue").value("Đỏ"))
                .andExpect(jsonPath("$.data.options[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.options[0].sortOrder").value(0));

        // Thêm tùy chọn "blue" -> sortOrder=1
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "blue",
                                  "displayValue": "Xanh"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.options.length()").value(2))
                .andExpect(jsonPath("$.data.options[1].value").value("blue"))
                .andExpect(jsonPath("$.data.options[1].sortOrder").value(1));
    }

    /**
     * 12. addOptionRejectsBadValues: value "Red", "đỏ", "do tuoi", "-red", "red-", "a--b" -> 400 CATALOG_FIELD_INVALID;
     * value trùng ("red" lần hai) -> 409 ATTRIBUTE_OPTION_VALUE_TAKEN; không thêm tùy chọn nào ngoài những cái hợp lệ trước đó.
     */
    @Test
    void addOptionRejectsBadValues() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // Thêm tùy chọn "red" hợp lệ đầu tiên
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isCreated());

        String[] badValues = {"Red", "đỏ", "do tuoi", "-red", "red-", "a--b"};
        for (String badVal : badValues) {
            mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                            .with(admin(ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "value": "%s",
                                      "displayValue": "Không hợp lệ"
                                    }
                                    """.formatted(badVal)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));
        }

        // Trùng value "red" lần hai -> 409 ATTRIBUTE_OPTION_VALUE_TAKEN
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ khác"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_OPTION_VALUE_TAKEN"));

        // Kiểm tra không thêm tùy chọn nào ngoài "red" hợp lệ
        Integer optionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attribute_option WHERE template_id = ?",
                Integer.class, UUID.fromString(templateId));
        assertThat(optionCount).isEqualTo(1);
    }

    /**
     * 13. optionValueAllowsHyphenAndUnderscore: thêm "usb-c" và "x_large" -> 201 cả hai;
     * "usb-c" và "usb_c" là hai giá trị khác nhau (thêm cả hai đều 201).
     */
    @Test
    void optionValueAllowsHyphenAndUnderscore() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "connector_type",
                                  "displayName": "Loại cổng",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // Thêm "usb-c" -> 201
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "usb-c",
                                  "displayValue": "USB-C"
                                }
                                """))
                .andExpect(status().isCreated());

        // Thêm "x_large" -> 201
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "x_large",
                                  "displayValue": "X Large"
                                }
                                """))
                .andExpect(status().isCreated());

        // Thêm "usb_c" -> 201 (khác với usb-c)
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "usb_c",
                                  "displayValue": "USB_C"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    /**
     * 14. addOptionToNonSelectOrInactiveTemplateIsConflict: mẫu TEXT -> 409 ATTRIBUTE_INPUT_TYPE_NOT_SELECT;
     * mẫu SINGLE_SELECT đã deactivate -> 409 ATTRIBUTE_TEMPLATE_INACTIVE.
     */
    @Test
    void addOptionToNonSelectOrInactiveTemplateIsConflict() throws Exception {
        // 1. Mẫu TEXT -> 409 ATTRIBUTE_INPUT_TYPE_NOT_SELECT
        var textRes = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "description",
                                  "displayName": "Mô tả",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String textTemplateId = json(textRes).path("data").path("id").asText();

        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", textTemplateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "opt",
                                  "displayValue": "Tùy chọn"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_INPUT_TYPE_NOT_SELECT"));

        // 2. Mẫu SINGLE_SELECT đã deactivate -> 409 ATTRIBUTE_TEMPLATE_INACTIVE
        var selectRes = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String selectTemplateId = json(selectRes).path("data").path("id").asText();

        mockMvc.perform(post("/api/admin/attribute-templates/{id}/deactivate", selectTemplateId)
                        .with(admin(ADMIN)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", selectTemplateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_TEMPLATE_INACTIVE"));
    }

    /**
     * 15. updateOptionLabelAnyTimeButValueNeverChanges: PUT options/{optionId} {"displayValue":"Đỏ đậm"}
     * -> 200, displayValue đổi, value vẫn "red"; làm được cả khi mẫu đã deactivate;
     * optionId lạ (UUID ngẫu nhiên) -> 404 ATTRIBUTE_OPTION_NOT_FOUND.
     */
    @Test
    void updateOptionLabelAnyTimeButValueNeverChanges() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // Thêm tùy chọn "red"
        var optResult = mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String optionId = json(optResult).path("data").path("options").get(0).path("id").asText();

        // Deactivate mẫu
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/deactivate", templateId)
                        .with(admin(ADMIN)))
                .andExpect(status().isOk());

        // Cập nhật nhãn khi mẫu đã deactivate -> 200
        mockMvc.perform(put("/api/admin/attribute-templates/{id}/options/{optionId}", templateId, optionId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayValue": "Đỏ đậm"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.options[0].value").value("red"))
                .andExpect(jsonPath("$.data.options[0].displayValue").value("Đỏ đậm"));

        // optionId lạ -> 404 ATTRIBUTE_OPTION_NOT_FOUND
        UUID randomOptionId = UUID.randomUUID();
        mockMvc.perform(put("/api/admin/attribute-templates/{id}/options/{optionId}", templateId, randomOptionId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayValue": "Không tìm thấy"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_OPTION_NOT_FOUND"));
    }

    /**
     * 16. deactivateAndActivateOption: deactivate tùy chọn -> 200 status=INACTIVE; lần nữa -> 409 CATALOG_INVALID_STATE;
     * activate -> 200 ACTIVE; ngừng tùy chọn cuối cùng của mẫu được phép (200) và mẫu thành usable=false.
     */
    @Test
    void deactivateAndActivateOption() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // Thêm tùy chọn duy nhất
        var optResult = mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String optionId = json(optResult).path("data").path("options").get(0).path("id").asText();

        // Deactivate tùy chọn (tùy chọn cuối cùng) -> 200, status=INACTIVE, usable=false
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options/{optionId}/deactivate", templateId, optionId)
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usable").value(false))
                .andExpect(jsonPath("$.data.options[0].status").value("INACTIVE"));

        // Deactivate lần nữa -> 409 CATALOG_INVALID_STATE
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options/{optionId}/deactivate", templateId, optionId)
                        .with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));

        // Activate tùy chọn -> 200, status=ACTIVE, usable=true
        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options/{optionId}/activate", templateId, optionId)
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usable").value(true))
                .andExpect(jsonPath("$.data.options[0].status").value("ACTIVE"));
    }

    /**
     * 17. reorderOptions: mẫu có 3 tùy chọn A,B,C; PUT order [C,A,B] -> 200, sortOrder theo thứ tự mới (C=0,A=1,B=2);
     * thiếu một id -> 400 ATTRIBUTE_INVALID_OPTION_ORDER; lặp id -> 400 ATTRIBUTE_INVALID_OPTION_ORDER;
     * id lạ -> 400 ATTRIBUTE_INVALID_OPTION_ORDER; thứ tự trong cơ sở dữ liệu không đổi sau ca lỗi.
     */
    @Test
    void reorderOptions() throws Exception {
        var createResult = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color",
                                  "displayName": "Màu sắc",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String templateId = json(createResult).path("data").path("id").asText();

        // Thêm 3 tùy chọn: opt_a, opt_b, opt_c
        var resA = mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"opt_a","displayValue":"A"}
                                """))
                .andReturn();
        String idA = json(resA).path("data").path("options").get(0).path("id").asText();

        var resB = mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"opt_b","displayValue":"B"}
                                """))
                .andReturn();
        String idB = json(resB).path("data").path("options").get(1).path("id").asText();

        var resC = mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":"opt_c","displayValue":"C"}
                                """))
                .andReturn();
        String idC = json(resC).path("data").path("options").get(2).path("id").asText();

        // 1. PUT order [C, A, B] -> 200
        mockMvc.perform(put("/api/admin/attribute-templates/{id}/options/order", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "optionIds": ["%s", "%s", "%s"]
                                }
                                """.formatted(idC, idA, idB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.options[0].id").value(idC))
                .andExpect(jsonPath("$.data.options[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data.options[1].id").value(idA))
                .andExpect(jsonPath("$.data.options[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data.options[2].id").value(idB))
                .andExpect(jsonPath("$.data.options[2].sortOrder").value(2));

        // 2. Thiếu một id -> 400 ATTRIBUTE_INVALID_OPTION_ORDER
        mockMvc.perform(put("/api/admin/attribute-templates/{id}/options/order", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "optionIds": ["%s", "%s"]
                                }
                                """.formatted(idC, idA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_INVALID_OPTION_ORDER"));

        // 3. Lặp id -> 400 ATTRIBUTE_INVALID_OPTION_ORDER
        mockMvc.perform(put("/api/admin/attribute-templates/{id}/options/order", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "optionIds": ["%s", "%s", "%s"]
                                }
                                """.formatted(idC, idA, idA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_INVALID_OPTION_ORDER"));

        // 4. Id lạ -> 400 ATTRIBUTE_INVALID_OPTION_ORDER
        mockMvc.perform(put("/api/admin/attribute-templates/{id}/options/order", templateId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "optionIds": ["%s", "%s", "%s"]
                                }
                                """.formatted(idC, idA, UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("ATTRIBUTE_INVALID_OPTION_ORDER"));

        // Thứ tự trong cơ sở dữ liệu không đổi sau các ca lỗi (C=0, A=1, B=2)
        Integer sortC = jdbcTemplate.queryForObject(
                "SELECT sort_order FROM attribute_option WHERE id = ?", Integer.class, UUID.fromString(idC));
        Integer sortA = jdbcTemplate.queryForObject(
                "SELECT sort_order FROM attribute_option WHERE id = ?", Integer.class, UUID.fromString(idA));
        Integer sortB = jdbcTemplate.queryForObject(
                "SELECT sort_order FROM attribute_option WHERE id = ?", Integer.class, UUID.fromString(idB));
        assertThat(sortC).isEqualTo(0);
        assertThat(sortA).isEqualTo(1);
        assertThat(sortB).isEqualTo(2);
    }

    /**
     * 18. listFiltersAndPaginates: tạo vài mẫu (kiểu khác nhau, một cái deactivate); GET list không lọc total đúng;
     * ?inputType=NUMBER chỉ NUMBER; ?status=INACTIVE chỉ cái đã ngừng; ?keyword= khớp một phần name hoặc displayName
     * không phân biệt hoa thường; ?page=0&size=2 trả 2 phần tử; optionCount đúng số tùy chọn; usable đúng.
     */
    @Test
    void listFiltersAndPaginates() throws Exception {
        // Mẫu 1: NUMBER (usable=true, optionCount=0)
        mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "weight",
                                  "displayName": "Khối lượng",
                                  "inputType": "NUMBER",
                                  "unit": "kg"
                                }
                                """))
                .andExpect(status().isCreated());

        // Mẫu 2: SINGLE_SELECT có 1 tùy chọn (usable=true, optionCount=1)
        var selectRes = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "color_primary",
                                  "displayName": "Màu sắc chính",
                                  "inputType": "SINGLE_SELECT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String selectId = json(selectRes).path("data").path("id").asText();

        mockMvc.perform(post("/api/admin/attribute-templates/{id}/options", selectId)
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": "red",
                                  "displayValue": "Đỏ"
                                }
                                """))
                .andExpect(status().isCreated());

        // Mẫu 3: TEXT sau đó deactivate (INACTIVE, usable=false)
        var textRes = mockMvc.perform(post("/api/admin/attribute-templates")
                        .with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "description",
                                  "displayName": "Mô tả sản phẩm",
                                  "inputType": "TEXT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String textId = json(textRes).path("data").path("id").asText();

        mockMvc.perform(post("/api/admin/attribute-templates/{id}/deactivate", textId)
                        .with(admin(ADMIN)))
                .andExpect(status().isOk());

        // 1. GET list không lọc: total=3
        mockMvc.perform(get("/api/admin/attribute-templates").with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3));

        // 2. ?inputType=NUMBER chỉ NUMBER
        mockMvc.perform(get("/api/admin/attribute-templates")
                        .param("inputType", "NUMBER")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("weight"))
                .andExpect(jsonPath("$.data.items[0].inputType").value("NUMBER"))
                .andExpect(jsonPath("$.data.items[0].unit").value("kg"))
                .andExpect(jsonPath("$.data.items[0].usable").value(true))
                .andExpect(jsonPath("$.data.items[0].optionCount").value(0));

        // 3. ?status=INACTIVE chỉ cái đã ngừng
        mockMvc.perform(get("/api/admin/attribute-templates")
                        .param("status", "INACTIVE")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("description"))
                .andExpect(jsonPath("$.data.items[0].status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.items[0].usable").value(false));

        // 4. ?keyword= khớp một phần name hoặc displayName không phân biệt hoa thường
        mockMvc.perform(get("/api/admin/attribute-templates")
                        .param("keyword", "PRIMARY")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("color_primary"))
                .andExpect(jsonPath("$.data.items[0].optionCount").value(1))
                .andExpect(jsonPath("$.data.items[0].usable").value(true));

        // 5. ?page=0&size=2 trả 2 phần tử
        mockMvc.perform(get("/api/admin/attribute-templates")
                        .param("page", "0")
                        .param("size", "2")
                        .with(admin(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }
}