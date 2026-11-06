package vn.t3nexus.catalog.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.catalog.domain.product.ProductRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KT-07: lệnh biến thể lỗi giữa chừng hoàn tác cả giao dịch. Lỗi được ép ở lần ghi sản phẩm (bước cuối, sau khi biến thể
 * đã được ghi) nên biến thể vừa ghi và phiên bản sản phẩm đều phải trở về như cũ. Feature 07 không có hộp thư và kiểm toán.
 */
class SellerRollbackTest extends SellerApiTestBase {

    @MockitoSpyBean
    private ProductRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID leaf;
    private UUID color;
    private UUID red;
    private UUID product;

    @BeforeEach
    void structure() throws Exception {
        Mockito.reset(productRepository);
        leaf = createLeaf("Áo");
        color = createTemplate("color", "SINGLE_SELECT");
        red = addOption(color, "red");
        UUID blue = addOption(color, "blue");
        assignAttributes(leaf, "[" + item(color, false, 1) + "]");
        product = createProduct(SELLER, leaf, "[" + axis(color, red, blue) + "]");
    }

    /** {@code save} đòi giao dịch (MANDATORY): khai báo giả lập trong một giao dịch rồi bỏ. */
    private void failProductSave() {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> doThrow(new IllegalStateException("Lỗi ghi sản phẩm")).when(productRepository).save(any()));
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    @DisplayName("Thêm biến thể: ghi sản phẩm lỗi thì biến thể và các cặp tổ hợp cũng không còn, phiên bản giữ nguyên")
    void addVariantRollsBack() throws Exception {
        long outboxBefore = count("outbox_events");
        failProductSave();

        mockMvc.perform(post("/api/seller/products/" + product + "/variants").with(user(SELLER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"combination\":[" + pair(color, red) + "],\"price\":100000,\"sku\":\"SKU-1\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(count("variant")).isZero();
        assertThat(count("variant_combination_item")).isZero();
        assertThat(count("outbox_events")).isEqualTo(outboxBefore);
        Mockito.reset(productRepository);
        assertThat(versionOf(SELLER, product)).isZero();
    }

    @Test
    @DisplayName("Ngừng biến thể: ghi sản phẩm lỗi thì trạng thái biến thể về như cũ")
    void deactivateRollsBack() throws Exception {
        UUID variant = createVariant(SELLER, product, "[" + pair(color, red) + "]", 100_000L, null);
        long before = versionOf(SELLER, product);
        Mockito.reset(productRepository);
        failProductSave();

        mockMvc.perform(post("/api/seller/products/" + product + "/variants/" + variant + "/deactivate").with(user(SELLER)))
                .andExpect(status().isInternalServerError());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM variant", String.class)).isEqualTo("ACTIVE");
        Mockito.reset(productRepository);
        assertThat(versionOf(SELLER, product)).isEqualTo(before);
    }
}
