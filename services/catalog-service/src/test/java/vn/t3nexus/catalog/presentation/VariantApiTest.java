package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KT-05 phần biến thể: thêm, đổi giá và SKU, ngừng, bật lại; 401, 403 (kể cả ADMIN), 404, 400, 409 từng mã; mỗi lệnh thành
 * công tăng phiên bản sản phẩm đúng một; không có đường sửa tổ hợp, xóa riêng một biến thể, khai trục riêng (E15).
 */
class VariantApiTest extends SellerApiTestBase {

    private UUID leaf;
    private UUID color;
    private UUID size;
    private UUID red;
    private UUID blue;
    private UUID m;
    private UUID l;
    private UUID product;

    @BeforeEach
    void structure() throws Exception {
        leaf = createLeaf("Áo");
        color = createTemplate("color", "SINGLE_SELECT");
        size = createTemplate("size_label", "SINGLE_SELECT");
        red = addOption(color, "red");
        blue = addOption(color, "blue");
        m = addOption(size, "m");
        l = addOption(size, "l");
        assignAttributes(leaf, "[" + item(color, false, 1) + "," + item(size, false, 2) + "]");
        product = createProduct(SELLER, leaf, "[" + axis(color, red, blue) + "," + axis(size, m, l) + "]");
    }

    private String combo(UUID colorOption, UUID sizeOption) {
        return "[" + pair(color, colorOption) + "," + pair(size, sizeOption) + "]";
    }

    // ---- xác thực và vai ----

    @Test
    @DisplayName("Không token 401; ADMIN 403 CATALOG_ROLE_NOT_ALLOWED; không ghi gì")
    void authAndRole() throws Exception {
        UUID variant = createVariant(SELLER, product, combo(red, m), 100_000L, null);
        String base = "/api/seller/products/" + product + "/variants";
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + "/" + variant + "/deactivate")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + "/" + variant + "/activate")).andExpect(status().isUnauthorized());

        mockMvc.perform(post(base).with(admin(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"combination\":" + combo(red, l) + ",\"price\":1000}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CATALOG_ROLE_NOT_ALLOWED"));
        mockMvc.perform(post(base + "/" + variant + "/deactivate").with(admin(ADMIN))).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM variant", Integer.class)).isEqualTo(1);
    }

    // ---- thêm ----

    @Test
    @DisplayName("Thêm: 201 biến thể ACTIVE; phiên bản sản phẩm tăng đúng một; chi tiết sản phẩm có biến thể; hết thiếu biến thể")
    void addVariant() throws Exception {
        long before = versionOf(SELLER, product);
        JsonNode created = json(postVariant(SELLER, product, "{\"combination\":" + combo(red, m) + ",\"price\":150000,\"sku\":\"SKU-1\"}")
                .andExpect(status().isCreated()).andReturn()).path("data");
        assertThat(created.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.path("price").asLong()).isEqualTo(150_000L);
        assertThat(created.path("sku").asText()).isEqualTo("SKU-1");
        assertThat(created.path("combination")).hasSize(2);
        assertThat(versionOf(SELLER, product)).isEqualTo(before + 1);

        JsonNode detail = json(getProduct(SELLER, product).andReturn()).path("data");
        assertThat(detail.path("variants")).hasSize(1);
        // Còn đúng một điều kiện thiếu: chưa có ảnh (feature 08); biến thể đã đủ nên không còn NO_ACTIVE_VARIANT
        assertThat(detail.path("missingPublishConditions")).hasSize(1);
        assertThat(detail.path("missingPublishConditions").get(0).path("code").asString()).isEqualTo("NO_IMAGE");
        assertThat(jdbcTemplate.queryForObject("SELECT seller_id::text FROM variant", String.class)).isEqualTo(SELLER.toString());
    }

    @Test
    @DisplayName("Thêm: tổ hợp trùng (kể cả khác thứ tự trục) 409 VARIANT_COMBINATION_TAKEN")
    void combinationTaken() throws Exception {
        createVariant(SELLER, product, combo(red, m), 100_000L, null);
        postVariant(SELLER, product, "{\"combination\":" + combo(red, m) + ",\"price\":100000}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VARIANT_COMBINATION_TAKEN"));
        postVariant(SELLER, product, "{\"combination\":[" + pair(size, m) + "," + pair(color, red) + "],\"price\":100000}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VARIANT_COMBINATION_TAKEN"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM variant", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Thêm: tổ hợp thiếu trục, thừa, ngoài tập trục: 400 VARIANT_COMBINATION_INVALID")
    void combinationInvalid() throws Exception {
        UUID green = addOption(color, "green");
        for (String combination : new String[]{"[" + pair(color, red) + "]", "[]", combo(green, m),
                "[" + pair(color, red) + "," + pair(color, blue) + "," + pair(size, m) + "]"}) {
            postVariant(SELLER, product, "{\"combination\":" + combination + ",\"price\":100000}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VARIANT_COMBINATION_INVALID"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM variant", Integer.class)).isZero();
        assertThat(versionOf(SELLER, product)).isZero();
    }

    @Test
    @DisplayName("Thêm: giá không nguyên, ≤ 0, quá 1 tỷ; SKU rỗng hoặc quá 50: 400 CATALOG_FIELD_INVALID")
    void priceAndSkuErrors() throws Exception {
        String combination = combo(red, m);
        for (String body : new String[]{
                "{\"combination\":" + combination + ",\"price\":1.5}",
                "{\"combination\":" + combination + ",\"price\":\"100000\"}",
                "{\"combination\":" + combination + ",\"price\":0}",
                "{\"combination\":" + combination + ",\"price\":-5}",
                "{\"combination\":" + combination + ",\"price\":1000000001}",
                "{\"combination\":" + combination + "}",
                "{\"combination\":" + combination + ",\"price\":1000,\"sku\":\"\"}",
                "{\"combination\":" + combination + ",\"price\":1000,\"sku\":\"" + "x".repeat(51) + "\"}",
                "{\"combination\":\"x\",\"price\":1000}"}) {
            postVariant(SELLER, product, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CATALOG_FIELD_INVALID"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM variant", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Thêm: SKU trùng trong người bán (kể cả sản phẩm khác) 409 VARIANT_SKU_TAKEN; người bán khác dùng được")
    void skuTaken() throws Exception {
        createVariant(SELLER, product, combo(red, m), 100_000L, "SKU-1");
        postVariant(SELLER, product, "{\"combination\":" + combo(red, l) + ",\"price\":100000,\"sku\":\"SKU-1\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VARIANT_SKU_TAKEN"));

        UUID second = createProduct(SELLER, leaf, "[]");
        postVariant(SELLER, second, "{\"combination\":[],\"price\":100000,\"sku\":\"SKU-1\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VARIANT_SKU_TAKEN"));

        UUID theirs = createProduct(OTHER_SELLER, leaf, "[]");
        postVariant(OTHER_SELLER, theirs, "{\"combination\":[],\"price\":100000,\"sku\":\"SKU-1\"}")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Thêm vào sản phẩm của người khác hoặc không có: 404 PRODUCT_NOT_FOUND; mã sai dạng 400")
    void productOwnership() throws Exception {
        postVariant(OTHER_SELLER, product, "{\"combination\":" + combo(red, m) + ",\"price\":100000}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        postVariant(SELLER, UUID.randomUUID(), "{\"combination\":[],\"price\":100000}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(post("/api/seller/products/khong-phai-uuid/variants").with(user(SELLER))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Sản phẩm không có trục: biến thể mặc định với tổ hợp rỗng, chỉ một")
    void defaultVariant() throws Exception {
        UUID plain = createProduct(SELLER, leaf, "[]");
        createVariant(SELLER, plain, "[]", 99_000L, null);
        postVariant(SELLER, plain, "{\"price\":99000}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_COMBINATION_TAKEN"));
    }

    // ---- đổi giá, SKU ----

    @Test
    @DisplayName("Đổi giá và SKU: PATCH một phần, null bỏ SKU, mỗi lần tăng phiên bản sản phẩm một")
    void patchVariant() throws Exception {
        UUID variant = createVariant(SELLER, product, combo(red, m), 100_000L, "SKU-1");
        long before = versionOf(SELLER, product);

        JsonNode price = json(patchVariant(SELLER, product, variant, "{\"price\":120000}").andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(price.path("price").asLong()).isEqualTo(120_000L);
        assertThat(price.path("sku").asText()).isEqualTo("SKU-1");

        JsonNode noSku = json(patchVariant(SELLER, product, variant, "{\"sku\":null}").andExpect(status().isOk())
                .andReturn()).path("data");
        assertThat(noSku.path("sku").isNull()).isTrue();
        assertThat(versionOf(SELLER, product)).isEqualTo(before + 2);

        patchVariant(SELLER, product, variant, "{\"price\":2.5}").andExpect(status().isBadRequest());
        patchVariant(SELLER, product, variant, "{\"price\":null}").andExpect(status().isBadRequest());
        assertThat(versionOf(SELLER, product)).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("Đổi SKU sang SKU đã có 409; biến thể của sản phẩm khác 404 VARIANT_NOT_FOUND")
    void patchVariantErrors() throws Exception {
        UUID first = createVariant(SELLER, product, combo(red, m), 100_000L, "SKU-1");
        createVariant(SELLER, product, combo(red, l), 100_000L, "SKU-2");
        patchVariant(SELLER, product, first, "{\"sku\":\"SKU-2\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_SKU_TAKEN"));

        UUID other = createProduct(SELLER, leaf, "[]");
        patchVariant(SELLER, other, first, "{\"price\":5000}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_FOUND"));
        patchVariant(OTHER_SELLER, product, first, "{\"price\":5000}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    // ---- ngừng, bật lại ----

    @Test
    @DisplayName("Ngừng và bật lại: đổi trạng thái, phiên bản sản phẩm tăng một; lặp lại 409 CATALOG_INVALID_STATE không đổi gì")
    void deactivateActivate() throws Exception {
        UUID variant = createVariant(SELLER, product, combo(red, m), 100_000L, null);
        String base = "/api/seller/products/" + product + "/variants/" + variant;
        long before = versionOf(SELLER, product);

        mockMvc.perform(post(base + "/deactivate").with(user(SELLER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
        mockMvc.perform(post(base + "/deactivate").with(user(SELLER))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_INVALID_STATE"));
        assertThat(versionOf(SELLER, product)).isEqualTo(before + 1);
        JsonNode detail = json(getProduct(SELLER, product).andReturn()).path("data");
        assertThat(detail.path("missingPublishConditions").get(0).path("code").asText()).isEqualTo("NO_ACTIVE_VARIANT");

        mockMvc.perform(post(base + "/activate").with(user(SELLER))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(post(base + "/activate").with(user(SELLER))).andExpect(status().isConflict());
        assertThat(versionOf(SELLER, product)).isEqualTo(before + 2);

        mockMvc.perform(post("/api/seller/products/" + product + "/variants/" + UUID.randomUUID() + "/activate")
                .with(user(SELLER))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VARIANT_NOT_FOUND"));
        mockMvc.perform(post(base + "/activate").with(user(OTHER_SELLER))).andExpect(status().isNotFound());
    }

    // ---- khóa trục và đường không có ----

    @Test
    @DisplayName("Đã có biến thể: đổi tập mẫu làm trục 409 PRODUCT_AXES_LOCKED; bỏ giá trị đang dùng 409 PRODUCT_AXIS_VALUE_IN_USE (kể cả biến thể ngừng)")
    void axesLockedAfterVariants() throws Exception {
        UUID variant = createVariant(SELLER, product, combo(red, m), 100_000L, null);

        patchProduct(SELLER, product, "{\"attributes\":[" + axis(color, red, blue) + "]}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_AXES_LOCKED"));
        patchProduct(SELLER, product, "{\"attributes\":[" + axis(color, blue) + "," + axis(size, m, l) + "]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_AXIS_VALUE_IN_USE"));
        mockMvc.perform(post("/api/seller/products/" + product + "/variants/" + variant + "/deactivate").with(user(SELLER)))
                .andExpect(status().isOk());
        patchProduct(SELLER, product, "{\"attributes\":[" + axis(color, blue) + "," + axis(size, m, l) + "]}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_AXIS_VALUE_IN_USE"));

        patchProduct(SELLER, product, "{\"attributes\":[" + axis(color, red) + "," + axis(size, m)
                + "]}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Không có đường sửa tổ hợp, xóa riêng một biến thể, khai trục riêng (E15)")
    void noSuchRoutes() throws Exception {
        UUID variant = createVariant(SELLER, product, combo(red, m), 100_000L, null);
        String base = "/api/seller/products/" + product;
        mockMvc.perform(delete(base + "/variants/" + variant).with(user(SELLER))).andExpect(status().is4xxClientError());
        mockMvc.perform(put(base + "/variants/" + variant).with(user(SELLER)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().is4xxClientError());
        mockMvc.perform(put(base + "/axes").with(user(SELLER)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM variant", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Tổ hợp của biến thể không đổi được qua PATCH: trường combination bị bỏ qua")
    void combinationIsImmutable() throws Exception {
        UUID variant = createVariant(SELLER, product, combo(red, m), 100_000L, null);
        String key = jdbcTemplate.queryForObject("SELECT combination_key FROM variant", String.class);
        patchVariant(SELLER, product, variant, "{\"combination\":" + combo(blue, l) + ",\"price\":5000}")
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT combination_key FROM variant", String.class)).isEqualTo(key);
    }
}
