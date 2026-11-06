package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KT-05 phần sản phẩm: API người bán trên Postgres thật — 401, 403 (kể cả ADMIN), 404 (sản phẩm người khác như không
 * có), 400, 409 từng mã; thông số giữ nguyên giá trị khi Quản trị tắt tùy chọn (E4); ký tự đặc biệt lưu như chữ (E6);
 * trường chủ, trạng thái, phiên bản trong nội dung bị bỏ qua (E23).
 */
class ProductApiTest extends SellerApiTestBase {

    private UUID leaf;
    private UUID model;
    private UUID color;
    private UUID size;
    private UUID red;
    private UUID blue;
    private UUID m;
    private UUID l;

    @BeforeEach
    void structure() throws Exception {
        leaf = createLeaf("Điện thoại");
        model = createTemplate("model", "TEXT");
        color = createTemplate("color", "SINGLE_SELECT");
        size = createTemplate("size_label", "SINGLE_SELECT");
        red = addOption(color, "red");
        blue = addOption(color, "blue");
        m = addOption(size, "m");
        l = addOption(size, "l");
        assignAttributes(leaf, "[" + item(model, true, 1) + "," + item(color, false, 2) + "," + item(size, false, 3) + "]");
    }

    private String attributes(String... entries) {
        return "[" + String.join(",", entries) + "]";
    }

    // ---- xác thực và vai ----

    @Test
    @DisplayName("Không token: mọi đường 401")
    void unauthenticated() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/seller/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/products/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/seller/products/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN gọi đường người bán: 403 CATALOG_ROLE_NOT_ALLOWED, không đọc không ghi")
    void adminIsForbidden() throws Exception {
        UUID product = createProduct(SELLER, leaf, "[]");
        mockMvc.perform(post("/api/seller/products").with(admin(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Điện thoại X\",\"categoryId\":\"" + leaf + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));
        mockMvc.perform(get("/api/seller/products").with(admin(ADMIN))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/seller/products/" + product).with(admin(ADMIN))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/seller/products/" + product).with(admin(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Tiêu đề mới\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product", Integer.class)).isEqualTo(1);
    }

    // ---- tạo ----

    @Test
    @DisplayName("Tạo: 201, nháp của người gọi, phiên bản 0, thông số kèm tên và trục, điều kiện còn thiếu để đăng")
    void createDraft() throws Exception {
        JsonNode data = json(postProduct(SELLER, """
                {"title":"Điện thoại X","description":"Mô tả","categoryId":"%s","attributes":%s}
                """.formatted(leaf, attributes(scalar(model, "  X1  "), axis(color, red, blue))))
                .andExpect(status().isCreated()).andReturn()).path("data");

        assertThat(data.path("status").asText()).isEqualTo("DRAFT");
        assertThat(data.path("version").asLong()).isZero();
        assertThat(data.path("variants")).isEmpty();
        assertThat(data.path("categoryId").asText()).isEqualTo(leaf.toString());
        assertThat(data.path("attributes").get(0).path("value").asText()).isEqualTo("X1");
        JsonNode axis = data.path("attributes").get(1);
        assertThat(axis.path("axis").asBoolean()).isTrue();
        assertThat(axis.path("options")).hasSize(2);
        assertThat(data.path("missingPublishConditions").get(0).path("code").asText()).isEqualTo("NO_ACTIVE_VARIANT");

        String owner = jdbcTemplate.queryForObject("SELECT seller_id::text FROM product", String.class);
        assertThat(owner).isEqualTo(SELLER.toString());
    }

    @Test
    @DisplayName("Tạo: thiếu thuộc tính bắt buộc vẫn tạo được nháp, điều kiện thiếu nêu mẫu bắt buộc")
    void createReportsRequiredMissing() throws Exception {
        JsonNode data = json(postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"" + leaf + "\"}")
                .andExpect(status().isCreated()).andReturn()).path("data");
        assertThat(data.path("missingPublishConditions").get(0).path("code").asText()).isEqualTo("REQUIRED_ATTRIBUTE_MISSING");
        assertThat(data.path("missingPublishConditions").get(0).path("templateId").asText()).isEqualTo(model.toString());
    }

    @Test
    @DisplayName("Tạo: trường chủ, trạng thái, phiên bản gửi lên bị bỏ qua (E23)")
    void ignoresServerOwnedFields() throws Exception {
        JsonNode data = json(postProduct(SELLER, """
                {"title":"Điện thoại X","categoryId":"%s","status":"PUBLISHED","version":99,"sellerId":"%s","id":"%s"}
                """.formatted(leaf, OTHER_SELLER, UUID.randomUUID())).andExpect(status().isCreated()).andReturn())
                .path("data");
        assertThat(data.path("status").asText()).isEqualTo("DRAFT");
        assertThat(data.path("version").asLong()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT seller_id::text FROM product", String.class)).isEqualTo(SELLER.toString());
    }

    @Test
    @DisplayName("Mô tả có ký tự đặc biệt lưu nguyên như chữ (E6)")
    void specialCharactersStoredAsText() throws Exception {
        String special = "<script>alert('x')</script> & \\\"quote\\\"";
        UUID id = idOf(postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"description\":\"%s\",\"categoryId\":\"%s\"}"
                .formatted(special, leaf)).andExpect(status().isCreated()).andReturn());
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM product WHERE id = ?", String.class, id))
                .isEqualTo("<script>alert('x')</script> & \"quote\"");
    }

    @Test
    @DisplayName("Tạo sai: tiêu đề ngoài 5-200, mô tả quá 5.000, thiếu mã danh mục, sai kiểu: 400 CATALOG_FIELD_INVALID")
    void createFieldErrors() throws Exception {
        for (String body : new String[]{
                "{\"title\":\"abc\",\"categoryId\":\"" + leaf + "\"}",
                "{\"title\":\"" + "a".repeat(201) + "\",\"categoryId\":\"" + leaf + "\"}",
                "{\"title\":\"Điện thoại X\",\"description\":\"" + "d".repeat(5_001) + "\",\"categoryId\":\"" + leaf + "\"}",
                "{\"title\":\"Điện thoại X\"}",
                "{\"title\":123,\"categoryId\":\"" + leaf + "\"}",
                "{\"title\":\"Điện thoại X\",\"categoryId\":\"" + leaf + "\",\"attributes\":\"x\"}"}) {
            postProduct(SELLER, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Tạo: danh mục không có 404; không phải lá hoặc ngừng dùng 409 PRODUCT_CATEGORY_NOT_USABLE")
    void createCategoryErrors() throws Exception {
        postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        UUID root = createCategory("Gốc riêng", null);
        postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"" + root + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_CATEGORY_NOT_USABLE"));

        mockMvc.perform(post("/api/admin/categories/" + leaf + "/deactivate").with(admin(ADMIN))).andExpect(status().isOk());
        postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"" + leaf + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_CATEGORY_NOT_USABLE"));
    }

    @Test
    @DisplayName("Tạo: thương hiệu tùy chọn; có thì phải tồn tại (404) và đang dùng (409 PRODUCT_BRAND_NOT_USABLE)")
    void createBrandRules() throws Exception {
        UUID brand = createBrand("Apple");
        JsonNode data = json(postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"%s\",\"brandId\":\"%s\"}"
                .formatted(leaf, brand)).andExpect(status().isCreated()).andReturn()).path("data");
        assertThat(data.path("brandId").asText()).isEqualTo(brand.toString());

        postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"%s\",\"brandId\":\"%s\"}"
                .formatted(leaf, UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRAND_NOT_FOUND"));

        mockMvc.perform(post("/api/admin/brands/" + brand + "/deactivate").with(admin(ADMIN))).andExpect(status().isOk());
        postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"%s\",\"brandId\":\"%s\"}"
                .formatted(leaf, brand)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_BRAND_NOT_USABLE"));
    }

    @Test
    @DisplayName("Tạo: giá trị thuộc tính sai 400 PRODUCT_ATTRIBUTE_INVALID, trục sai 400 PRODUCT_AXIS_INVALID; không ghi gì")
    void createAttributeErrors() throws Exception {
        UUID other = createTemplate("other_attr", "TEXT");
        for (String attributes : new String[]{
                attributes(scalar(other, "x")),
                attributes(scalar(model, "a"), scalar(model, "b")),
                attributes("{\"templateId\":\"" + color + "\",\"values\":[\"" + UUID.randomUUID() + "\"]}")}) {
            postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"%s\",\"attributes\":%s}".formatted(leaf, attributes))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRODUCT_ATTRIBUTE_INVALID"));
        }
        postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"%s\",\"attributes\":%s}"
                .formatted(leaf, attributes(axis(model, red)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_AXIS_INVALID"));
        UUID third = createTemplate("third_axis", "SINGLE_SELECT");
        UUID thirdOption = addOption(third, "v1");
        assignAttributes(leaf, "[" + item(model, true, 1) + "," + item(color, false, 2) + "," + item(size, false, 3) + ","
                + item(third, false, 4) + "]");
        postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"categoryId\":\"%s\",\"attributes\":%s}".formatted(leaf,
                attributes(axis(color, red), axis(size, m), axis(third, thirdOption))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRODUCT_AXIS_INVALID"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product_attribute", Integer.class)).isZero();
    }

    // ---- đọc ----

    @Test
    @DisplayName("Xem: sản phẩm của người khác và mã không có cùng 404 PRODUCT_NOT_FOUND; mã sai dạng 400")
    void getOwnership() throws Exception {
        UUID id = createProduct(SELLER, leaf, "[]");
        getProduct(SELLER, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id.toString()));
        getProduct(OTHER_SELLER, id).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        getProduct(SELLER, UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get("/api/seller/products/khong-phai-uuid").with(user(SELLER))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Danh sách: chỉ sản phẩm của tôi, mới nhất trước, phân trang và lọc trạng thái, kèm số biến thể và khoảng giá")
    void listMine() throws Exception {
        UUID first = createProduct(SELLER, leaf, attributes(axis(color, red, blue)));
        Thread.sleep(5);
        UUID second = createProduct(SELLER, leaf, "[]");
        createProduct(OTHER_SELLER, leaf, "[]");
        createVariant(SELLER, first, "[" + pair(color, red) + "]", 100_000L, null);
        createVariant(SELLER, first, "[" + pair(color, blue) + "]", 250_000L, null);

        JsonNode page = json(mockMvc.perform(get("/api/seller/products").with(user(SELLER))).andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(second.toString());
        JsonNode withVariants = page.path("items").get(1);
        assertThat(withVariants.path("id").asText()).isEqualTo(first.toString());
        assertThat(withVariants.path("variantCount").asLong()).isEqualTo(2);
        assertThat(withVariants.path("minPrice").asLong()).isEqualTo(100_000L);
        assertThat(withVariants.path("maxPrice").asLong()).isEqualTo(250_000L);
        assertThat(page.path("items").get(0).path("variantCount").asLong()).isZero();
        assertThat(page.path("items").get(0).path("minPrice").isNull()).isTrue();

        JsonNode paged = json(mockMvc.perform(get("/api/seller/products?size=1&page=1").with(user(SELLER)))
                .andReturn()).path("data");
        assertThat(paged.path("items")).hasSize(1);
        assertThat(paged.path("total").asLong()).isEqualTo(2);
        JsonNode published = json(mockMvc.perform(get("/api/seller/products?status=PUBLISHED").with(user(SELLER)))
                .andReturn()).path("data");
        assertThat(published.path("total").asLong()).isZero();
    }

    // ---- sửa ----

    @Test
    @DisplayName("Sửa: PATCH một phần giữ trường vắng; null xóa mô tả và thương hiệu; phiên bản tăng một mỗi lần")
    void patchPartial() throws Exception {
        UUID brand = createBrand("Apple");
        UUID id = idOf(postProduct(SELLER, "{\"title\":\"Điện thoại X\",\"description\":\"Mô tả\",\"categoryId\":\"%s\",\"brandId\":\"%s\"}"
                .formatted(leaf, brand)).andExpect(status().isCreated()).andReturn());

        JsonNode titleOnly = json(patchProduct(SELLER, id, "{\"title\":\"Tiêu đề mới\"}").andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(titleOnly.path("title").asText()).isEqualTo("Tiêu đề mới");
        assertThat(titleOnly.path("description").asText()).isEqualTo("Mô tả");
        assertThat(titleOnly.path("brandId").asText()).isEqualTo(brand.toString());
        assertThat(titleOnly.path("version").asLong()).isEqualTo(1);

        JsonNode cleared = json(patchProduct(SELLER, id, "{\"description\":null,\"brandId\":null}")
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(cleared.path("description").isNull()).isTrue();
        assertThat(cleared.path("brandId").isNull()).isTrue();
        assertThat(cleared.path("version").asLong()).isEqualTo(2);

        patchProduct(SELLER, id, "{\"title\":null}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));
        assertThat(versionOf(SELLER, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Sửa: sản phẩm của người khác 404; ADMIN 403; nội dung không phải đối tượng 400")
    void patchOwnership() throws Exception {
        UUID id = createProduct(SELLER, leaf, "[]");
        patchProduct(OTHER_SELLER, id, "{\"title\":\"Tiêu đề mới\"}").andExpect(status().isNotFound());
        patchProduct(SELLER, id, "[]").andExpect(status().isBadRequest());
        assertThat(versionOf(SELLER, id)).isZero();
    }

    @Test
    @DisplayName("Sửa thông số: thay toàn bộ danh sách, kể cả trục; gửi danh sách rỗng thì bỏ hết")
    void patchReplacesAttributes() throws Exception {
        UUID id = createProduct(SELLER, leaf, attributes(scalar(model, "X1"), axis(color, red)));

        JsonNode replaced = json(patchProduct(SELLER, id, "{\"attributes\":" + attributes(axis(size, m, l), scalar(model, "X2")) + "}")
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(replaced.path("attributes")).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product_attribute WHERE axis", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product_attribute_option", Integer.class)).isEqualTo(2);

        JsonNode emptied = json(patchProduct(SELLER, id, "{\"attributes\":[]}").andExpect(status().isOk()).andReturn())
                .path("data");
        assertThat(emptied.path("attributes")).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product_attribute", Integer.class)).isZero();
        assertThat(emptied.path("version").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("Quản trị tắt tùy chọn sau khi sản phẩm đã lưu: người bán sửa phần khác, giá trị cũ giữ nguyên (E4)")
    void adminDisablingOptionDoesNotStrandSeller() throws Exception {
        UUID id = createProduct(SELLER, leaf, attributes(scalar(model, "X1"), axis(color, red, blue)));
        deactivateOption(color, red);

        patchProduct(SELLER, id, "{\"title\":\"Tiêu đề mới\"}").andExpect(status().isOk());
        JsonNode kept = json(patchProduct(SELLER, id, "{\"attributes\":" + attributes(scalar(model, "X1"), axis(color, red, blue)) + "}")
                .andExpect(status().isOk()).andReturn()).path("data");
        JsonNode colorOptions = kept.path("attributes").get(1).path("options");
        assertThat(colorOptions).hasSize(2);
        boolean redUsable = true;
        for (JsonNode option : colorOptions) {
            if (option.path("id").asText().equals(red.toString())) {
                redUsable = option.path("usable").asBoolean();
            }
        }
        assertThat(redUsable).isFalse();

        patchProduct(SELLER, id, "{\"attributes\":" + attributes(axis(color, blue, red), axis(size, m)) + "}")
                .andExpect(status().isOk());
        UUID newest = addOption(color, "green");
        deactivateOption(color, newest);
        patchProduct(SELLER, id, "{\"attributes\":" + attributes(axis(color, blue, newest)) + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRODUCT_ATTRIBUTE_INVALID"));
    }

    @Test
    @DisplayName("INV-CAT-98: danh mục có sản phẩm tham chiếu không xóa được, lá không sản phẩm xóa được")
    void deleteCategoryReferencedByProductIsRejected() throws Exception {
        UUID categoryLeaf = createLeaf("Có sản phẩm");
        UUID productId = createProduct(SELLER, categoryLeaf, "[]");

        mockMvc.perform(delete("/api/admin/categories/" + categoryLeaf).with(admin(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_PRODUCTS"));

        String tree = mockMvc.perform(get("/api/admin/categories/tree").with(admin(ADMIN)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(tree).contains("Có sản phẩm 3");
        getProduct(SELLER, productId).andExpect(status().isOk());

        UUID unusedLeaf = createLeaf("Không sản phẩm");
        mockMvc.perform(delete("/api/admin/categories/" + unusedLeaf).with(admin(ADMIN)))
                .andExpect(status().isOk());
    }
}
