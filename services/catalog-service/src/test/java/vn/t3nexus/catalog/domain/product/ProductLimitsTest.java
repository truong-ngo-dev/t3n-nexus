package vn.t3nexus.catalog.domain.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Giới hạn Q-CAT-01 của sản phẩm và biến thể: mặc định theo phân tích, chỉ siết được, không nới quá ràng buộc V2. */
class ProductLimitsTest {

    private static final long PRICE = 1_000_000_000L;

    @Test
    @DisplayName("Mặc định: tiêu đề 5-200, mô tả 5.000, 2 trục, 50 biến thể, giá tối đa 1 tỷ, SKU 50")
    void defaults() {
        ProductLimits d = ProductLimits.defaults();
        assertThat(d.titleMin()).isEqualTo(5);
        assertThat(d.titleMax()).isEqualTo(200);
        assertThat(d.descriptionMax()).isEqualTo(5_000);
        assertThat(d.maxAxes()).isEqualTo(2);
        assertThat(d.maxVariants()).isEqualTo(50);
        assertThat(d.priceMax()).isEqualTo(PRICE);
        assertThat(d.skuMax()).isEqualTo(50);
    }

    @Test
    @DisplayName("Siết được; nới số biến thể (không có ràng buộc ở cơ sở dữ liệu) được")
    void tighteningAllowed() {
        ProductLimits l = new ProductLimits(10, 100, 1_000, 1, 200, 500_000_000L, 20);
        assertThat(l.maxVariants()).isEqualTo(200);
        assertThat(l.maxAxes()).isEqualTo(1);
    }

    @Test
    @DisplayName("Không nới quá ràng buộc V2 và không ngược biên: bị từ chối, thông báo nêu tên giới hạn")
    void outOfBoundsRejected() {
        assertInvalid("titleMin", () -> new ProductLimits(4, 200, 5_000, 2, 50, PRICE, 50));
        assertInvalid("titleMax", () -> new ProductLimits(5, 201, 5_000, 2, 50, PRICE, 50));
        assertInvalid("titleMax", () -> new ProductLimits(20, 10, 5_000, 2, 50, PRICE, 50));
        assertInvalid("descriptionMax", () -> new ProductLimits(5, 200, 5_001, 2, 50, PRICE, 50));
        assertInvalid("descriptionMax", () -> new ProductLimits(5, 200, 0, 2, 50, PRICE, 50));
        assertInvalid("maxAxes", () -> new ProductLimits(5, 200, 5_000, 3, 50, PRICE, 50));
        assertInvalid("maxAxes", () -> new ProductLimits(5, 200, 5_000, -1, 50, PRICE, 50));
        assertInvalid("maxVariants", () -> new ProductLimits(5, 200, 5_000, 2, 0, PRICE, 50));
        assertInvalid("priceMax", () -> new ProductLimits(5, 200, 5_000, 2, 50, PRICE + 1, 50));
        assertInvalid("priceMax", () -> new ProductLimits(5, 200, 5_000, 2, 50, 0L, 50));
        assertInvalid("skuMax", () -> new ProductLimits(5, 200, 5_000, 2, 50, PRICE, 51));
        assertInvalid("skuMax", () -> new ProductLimits(5, 200, 5_000, 2, 50, PRICE, 0));
    }

    @Test
    @DisplayName("Biên đúng được nhận")
    void boundariesAccepted() {
        new ProductLimits(5, 5, 1, 0, 1, 1L, 1);
        new ProductLimits(200, 200, 5_000, 2, 1, PRICE, 50);
    }

    private static void assertInvalid(String limit, Runnable build) {
        assertThatThrownBy(build::run)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(limit);
    }
}
