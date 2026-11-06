package vn.t3nexus.catalog.domain.variant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-02 cho AGG-CAT-05: biến thể — tổ hợp bất biến, khóa tổ hợp, giá, SKU, trạng thái (INV-CAT-40 đến 44, 92). */
class VariantTest {

    private static final Instant T0 = Instant.parse("2026-11-05T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-05T01:00:00Z");
    private static final UUID SELLER = UUID.fromString("00000000-0000-7000-8000-0000000000bb");
    private static final ProductLimits LIMITS = ProductLimits.defaults();

    // Mã mẫu có thứ tự chữ xác định: A < B.
    private static final AttributeTemplateId A = AttributeTemplateId.of(UUID.fromString("00000000-0000-7000-8000-00000000000a"));
    private static final AttributeTemplateId B = AttributeTemplateId.of(UUID.fromString("00000000-0000-7000-8000-00000000000b"));
    private static final AttributeOptionId RED = AttributeOptionId.of(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private static final AttributeOptionId M = AttributeOptionId.of(UUID.fromString("00000000-0000-7000-8000-000000000002"));

    private static Variant variant(long price, String sku, VariantAttributePair... pairs) {
        return Variant.create(VariantId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SELLER, List.of(pairs),
                price, sku, LIMITS, T0);
    }

    private static Variant variant() {
        return variant(150_000L, "SKU-1", new VariantAttributePair(A, RED), new VariantAttributePair(B, M));
    }

    // ---- tạo ----

    @Test
    @DisplayName("Tạo: ACTIVE, chủ sao từ sản phẩm, giá và SKU đúng, không phát event")
    void create() {
        Variant v = variant();
        assertThat(v.getStatus()).isEqualTo(VariantStatus.ACTIVE);
        assertThat(v.isActive()).isTrue();
        assertThat(v.getSellerId()).isEqualTo(SELLER);
        assertThat(v.getPrice()).isEqualTo(150_000L);
        assertThat(v.getSku()).isEqualTo("SKU-1");
        assertThat(v.getCombination()).hasSize(2);
        assertThat(v.getCreatedAt()).isEqualTo(T0);
        assertThat(v.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Khóa tổ hợp: mã tùy chọn sắp theo mã mẫu, nối dấu phẩy, không phụ thuộc thứ tự cặp (INV-CAT-92)")
    void combinationKeyIsOrderIndependent() {
        String expected = RED.getValue() + "," + M.getValue();
        assertThat(variant(1L, null, new VariantAttributePair(A, RED), new VariantAttributePair(B, M)).getCombinationKey())
                .isEqualTo(expected);
        assertThat(variant(1L, null, new VariantAttributePair(B, M), new VariantAttributePair(A, RED)).getCombinationKey())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("Khóa tổ hợp: khác tổ hợp thì khác khóa; một trục thì chỉ có mã tùy chọn; tổ hợp rỗng cho khóa rỗng (INV-CAT-44)")
    void combinationKeyVariants() {
        assertThat(variant(1L, null, new VariantAttributePair(A, RED)).getCombinationKey()).isEqualTo(RED.getValue().toString());
        assertThat(variant(1L, null, new VariantAttributePair(A, M)).getCombinationKey())
                .isNotEqualTo(variant(1L, null, new VariantAttributePair(A, RED)).getCombinationKey());
        Variant defaultVariant = variant(1L, null);
        assertThat(defaultVariant.getCombinationKey()).isEmpty();
        assertThat(defaultVariant.getCombination()).isEmpty();
    }

    @Test
    @DisplayName("Tổ hợp bất biến: danh sách trả về không sửa được")
    void combinationIsImmutable() {
        Variant v = variant();
        assertThatThrownBy(() -> v.getCombination().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- giá ----

    @Test
    @DisplayName("Giá: 1 đến 1 tỷ; 0, âm, quá 1 tỷ là 400 trường price")
    void priceBounds() {
        assertThat(variant(1L, null).getPrice()).isEqualTo(1L);
        assertThat(variant(1_000_000_000L, null).getPrice()).isEqualTo(1_000_000_000L);
        for (long bad : new long[]{0L, -1L, 1_000_000_001L}) {
            assertThatThrownBy(() -> variant(bad, null)).isInstanceOfSatisfying(CatalogFieldException.class,
                    e -> assertThat(e.field()).isEqualTo("price"));
        }
    }

    @Test
    @DisplayName("Giá tối đa theo giới hạn cấu hình")
    void priceLimitConfigurable() {
        ProductLimits tight = new ProductLimits(5, 200, 5_000, 2, 50, 1_000L, 50);
        assertThatThrownBy(() -> Variant.create(VariantId.of(UUID.randomUUID()), ProductId.of(UUID.randomUUID()), SELLER,
                List.of(), 1_001L, null, tight, T0)).isInstanceOf(CatalogFieldException.class);
    }

    @Test
    @DisplayName("Đổi giá: kiểm như lúc tạo và cập nhật thời điểm sửa")
    void changePrice() {
        Variant v = variant();
        v.changePrice(200_000L, LIMITS, T1);
        assertThat(v.getPrice()).isEqualTo(200_000L);
        assertThat(v.getUpdatedAt()).isEqualTo(T1);
        assertThatThrownBy(() -> v.changePrice(0L, LIMITS, T1)).isInstanceOf(CatalogFieldException.class);
        assertThat(v.getPrice()).isEqualTo(200_000L);
    }

    // ---- SKU ----

    @Test
    @DisplayName("SKU: tùy chọn; có thì cắt khoảng trắng, 1 đến 50 ký tự; rỗng hoặc quá 50 là 400 trường sku")
    void skuRules() {
        assertThat(variant(1L, null).getSku()).isNull();
        assertThat(variant(1L, "  AB-1  ").getSku()).isEqualTo("AB-1");
        assertThat(variant(1L, "x".repeat(50)).getSku()).hasSize(50);
        for (String bad : new String[]{"", "   ", "x".repeat(51)}) {
            assertThatThrownBy(() -> variant(1L, bad)).isInstanceOfSatisfying(CatalogFieldException.class,
                    e -> assertThat(e.field()).isEqualTo("sku"));
        }
    }

    @Test
    @DisplayName("Đổi SKU: đặt mới, bỏ bằng null, kiểm như lúc tạo")
    void changeSku() {
        Variant v = variant();
        v.changeSku("NEW-1", LIMITS, T1);
        assertThat(v.getSku()).isEqualTo("NEW-1");
        assertThat(v.getUpdatedAt()).isEqualTo(T1);
        v.changeSku(null, LIMITS, T1);
        assertThat(v.getSku()).isNull();
        assertThatThrownBy(() -> v.changeSku("", LIMITS, T1)).isInstanceOf(CatalogFieldException.class);
    }

    // ---- trạng thái ----

    @Test
    @DisplayName("Ngừng rồi bật lại; lặp lại cùng trạng thái là 409 CATALOG_INVALID_STATE")
    void activateDeactivate() {
        Variant v = variant();
        v.deactivate(T1);
        assertThat(v.getStatus()).isEqualTo(VariantStatus.INACTIVE);
        assertThat(v.getUpdatedAt()).isEqualTo(T1);
        assertInvalidState(() -> v.deactivate(T1));
        v.activate(T1);
        assertThat(v.isActive()).isTrue();
        assertInvalidState(() -> v.activate(T1));
    }

    private static void assertInvalidState(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CatalogErrorCode.CATALOG_INVALID_STATE));
    }
}
