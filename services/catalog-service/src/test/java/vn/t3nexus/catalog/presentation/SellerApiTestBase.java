package vn.t3nexus.catalog.presentation;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nền test API của người bán (feature 07). Cấu trúc (mẫu, tùy chọn, danh mục, dòng gán, thương hiệu) dựng bằng chính API
 * quản trị của feature 05, nên test sản phẩm chạy trên dữ liệu dựng đúng như người dùng thật dựng.
 */
abstract class SellerApiTestBase extends CatalogApiTestBase {

    protected static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-0000000000aa");
    protected static final UUID SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000bb");
    protected static final UUID OTHER_SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000cc");

    // ---- cấu trúc qua API quản trị ----

    protected UUID createCategory(String name, UUID parentId) throws Exception {
        String body = parentId == null ? "{\"name\":\"%s\"}".formatted(name)
                : "{\"name\":\"%s\",\"parentId\":\"%s\"}".formatted(name, parentId);
        return idOf(mockMvc.perform(post("/api/admin/categories").with(admin(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn());
    }

    /** Nhánh ba cấp; trả mã của lá. */
    protected UUID createLeaf(String prefix) throws Exception {
        UUID root = createCategory(prefix + " 1", null);
        UUID sub = createCategory(prefix + " 2", root);
        return createCategory(prefix + " 3", sub);
    }

    protected UUID createTemplate(String name, String inputType) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/attribute-templates").with(admin(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"displayName\":\"Hiển thị %s\",\"inputType\":\"%s\"}"
                        .formatted(name, name, inputType)))
                .andExpect(status().isCreated()).andReturn());
    }

    /** Thêm tùy chọn và trả về mã tùy chọn. */
    protected UUID addOption(UUID templateId, String value) throws Exception {
        mockMvc.perform(post("/api/admin/attribute-templates/" + templateId + "/options").with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"%s\",\"displayValue\":\"Tùy chọn %s\"}".formatted(value, value)))
                .andExpect(status().isCreated());
        JsonNode template = json(mockMvc.perform(get("/api/admin/attribute-templates/" + templateId)
                .with(admin(ADMIN))).andExpect(status().isOk()).andReturn()).path("data");
        for (JsonNode option : template.path("options")) {
            if (value.equals(option.path("value").asText())) {
                return UUID.fromString(option.path("id").asText());
            }
        }
        throw new IllegalStateException("Không thấy tùy chọn " + value);
    }

    protected void deactivateOption(UUID templateId, UUID optionId) throws Exception {
        mockMvc.perform(post("/api/admin/attribute-templates/" + templateId + "/options/" + optionId + "/deactivate")
                .with(admin(ADMIN))).andExpect(status().isOk());
    }

    /** {@code items}: mảng JSON các dòng gán {@code {templateId, required, displayOrder, constraints?}}. */
    protected void assignAttributes(UUID leaf, String items) throws Exception {
        mockMvc.perform(put("/api/admin/categories/" + leaf + "/attributes").with(admin(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":" + items + "}"))
                .andExpect(status().isOk());
    }

    protected String item(UUID templateId, boolean required, int order) {
        return "{\"templateId\":\"%s\",\"required\":%s,\"displayOrder\":%d}".formatted(templateId, required, order);
    }

    protected UUID createBrand(String name) throws Exception {
        return idOf(mockMvc.perform(post("/api/admin/brands").with(admin(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn());
    }

    // ---- lệnh của người bán ----

    /** Bản sao trạng thái người bán `APPROVED` (như sau khi nhận `seller.profile.approved`); tạo nháp cần nó (INV-CAT-30). */
    protected void approve(UUID account) {
        jdbcTemplate.update("INSERT INTO seller_state (account_id, status, version, updated_at) VALUES (?, 'APPROVED', 0, now()) "
                + "ON CONFLICT (account_id) DO UPDATE SET status = 'APPROVED'", account);
    }

    /** Tạo sản phẩm sau khi duyệt người bán; test về người bán chưa duyệt dùng {@link #postProductAsIs}. */
    protected ResultActions postProduct(UUID account, String body) throws Exception {
        approve(account);
        return postProductAsIs(account, body);
    }

    protected ResultActions postProductAsIs(UUID account, String body) throws Exception {
        return mockMvc.perform(post("/api/seller/products").with(user(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions patchProduct(UUID account, UUID productId, String body) throws Exception {
        return mockMvc.perform(patch("/api/seller/products/" + productId).with(user(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions getProduct(UUID account, UUID productId) throws Exception {
        return mockMvc.perform(get("/api/seller/products/" + productId).with(user(account)));
    }

    protected ResultActions postVariant(UUID account, UUID productId, String body) throws Exception {
        return mockMvc.perform(post("/api/seller/products/" + productId + "/variants").with(user(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions patchVariant(UUID account, UUID productId, UUID variantId, String body) throws Exception {
        return mockMvc.perform(patch("/api/seller/products/" + productId + "/variants/" + variantId).with(user(account))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected UUID createProduct(UUID account, UUID leaf, String attributes) throws Exception {
        return idOf(postProduct(account, "{\"title\":\"Điện thoại X\",\"categoryId\":\"%s\",\"attributes\":%s}"
                .formatted(leaf, attributes)).andExpect(status().isCreated()).andReturn());
    }

    protected UUID createVariant(UUID account, UUID productId, String combination, long price, String sku)
            throws Exception {
        String skuPart = sku == null ? "" : ",\"sku\":\"%s\"".formatted(sku);
        return idOf(postVariant(account, productId, "{\"combination\":%s,\"price\":%d%s}"
                .formatted(combination, price, skuPart)).andExpect(status().isCreated()).andReturn());
    }

    protected String pair(UUID templateId, UUID optionId) {
        return "{\"templateId\":\"%s\",\"optionId\":\"%s\"}".formatted(templateId, optionId);
    }

    protected String axis(UUID templateId, UUID... options) {
        StringBuilder values = new StringBuilder();
        for (UUID option : options) {
            values.append(values.isEmpty() ? "" : ",").append('"').append(option).append('"');
        }
        return "{\"templateId\":\"%s\",\"values\":[%s],\"axis\":true}".formatted(templateId, values);
    }

    protected String scalar(UUID templateId, String value) {
        return "{\"templateId\":\"%s\",\"values\":[\"%s\"]}".formatted(templateId, value);
    }

    protected long versionOf(UUID account, UUID productId) throws Exception {
        return json(getProduct(account, productId).andExpect(status().isOk()).andReturn()).path("data")
                .path("version").asLong();
    }

    protected UUID idOf(MvcResult result) throws Exception {
        return UUID.fromString(json(result).path("data").path("id").asText());
    }
}
