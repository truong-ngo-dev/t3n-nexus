package vn.t3nexus.catalog.domain.variant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductLimits;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KT-02: tổ hợp biến thể hợp lệ (INV-CAT-33, 94): đúng một giá trị cho mỗi trục, mỗi giá trị nằm trong tập trục của sản phẩm,
 * mẫu và tùy chọn còn dùng được lúc tạo. Sản phẩm không có trục chỉ nhận tổ hợp rỗng (biến thể mặc định, INV-CAT-44).
 */
class VariantCombinationPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    private static final ChangeContext CTX =
            new ChangeContext(new CatalogActor(UUID.randomUUID(), "ADMIN"), UUID.randomUUID(), NOW);

    private final AttributeTemplate color = template("color");
    private final AttributeTemplate size = template("size_label");
    private final AttributeOptionId red = option(color, "red");
    private final AttributeOptionId blue = option(color, "blue");
    private final AttributeOptionId green = option(color, "green");
    private final AttributeOptionId m = option(size, "m");
    private final AttributeOptionId l = option(size, "l");

    private static AttributeTemplate template(String name) {
        return AttributeTemplate.create(AttributeTemplateId.of(UUID.randomUUID()), name, "Hiển thị " + name, null,
                InputType.SINGLE_SELECT, null, CTX);
    }

    private static AttributeOptionId option(AttributeTemplate t, String value) {
        AttributeOptionId id = AttributeOptionId.of(UUID.randomUUID());
        t.addOption(id, value, "Tùy chọn " + value, CTX);
        return id;
    }

    private Product product(ProductAttribute... attributes) {
        return Product.create(ProductId.of(UUID.randomUUID()), UUID.randomUUID(), CategoryId.of(UUID.randomUUID()), null,
                "Áo thun", null, List.of(attributes), ProductLimits.defaults(), NOW);
    }

    private Product twoAxes() {
        return product(ProductAttribute.ofOptions(color.getId(), true, List.of(red, blue)),
                ProductAttribute.ofOptions(size.getId(), true, List.of(m, l)));
    }

    private Map<AttributeTemplateId, AttributeTemplate> templates() {
        Map<AttributeTemplateId, AttributeTemplate> map = new HashMap<>();
        map.put(color.getId(), color);
        map.put(size.getId(), size);
        return map;
    }

    private VariantAttributePair pair(AttributeTemplate t, AttributeOptionId o) {
        return new VariantAttributePair(t.getId(), o);
    }

    private void assertInvalid(Product product, VariantAttributePair... pairs) {
        assertThatThrownBy(() -> VariantCombinationPolicy.validate(product, List.of(pairs), templates()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(VariantErrorCode.VARIANT_COMBINATION_INVALID));
    }

    @Test
    @DisplayName("Đúng một giá trị cho mỗi trục, trong tập trục: hợp lệ, thứ tự cặp tùy ý")
    void validCombination() {
        VariantCombinationPolicy.validate(twoAxes(), List.of(pair(color, red), pair(size, m)), templates());
        VariantCombinationPolicy.validate(twoAxes(), List.of(pair(size, l), pair(color, blue)), templates());
    }

    @Test
    @DisplayName("Thiếu một trục bị từ chối")
    void missingAxis() {
        assertInvalid(twoAxes(), pair(color, red));
        assertInvalid(twoAxes());
    }

    @Test
    @DisplayName("Thừa cặp: mẫu không phải trục, hoặc sản phẩm không có trục mà vẫn gửi cặp")
    void extraPairs() {
        Product oneAxis = product(ProductAttribute.ofOptions(color.getId(), true, List.of(red)));
        assertInvalid(oneAxis, pair(color, red), pair(size, m));
        assertInvalid(product(), pair(color, red));
    }

    @Test
    @DisplayName("Hai giá trị cho cùng một trục bị từ chối")
    void duplicateAxis() {
        assertInvalid(twoAxes(), pair(color, red), pair(color, blue), pair(size, m));
    }

    @Test
    @DisplayName("Giá trị ngoài tập trục của sản phẩm bị từ chối dù là tùy chọn có thật của mẫu")
    void valueOutsideAxisSet() {
        assertInvalid(twoAxes(), pair(color, green), pair(size, m));
    }

    @Test
    @DisplayName("Giá trị không phải của mẫu trục bị từ chối")
    void valueFromAnotherTemplate() {
        assertInvalid(twoAxes(), pair(color, m), pair(size, l));
    }

    @Test
    @DisplayName("Tùy chọn đã ngừng hoặc mẫu đã ngừng: biến thể mới bị từ chối")
    void inactiveRejectedForNewVariant() {
        Product p = twoAxes();
        color.deactivateOption(red, CTX);
        assertInvalid(p, pair(color, red), pair(size, m));
        VariantCombinationPolicy.validate(p, List.of(pair(color, blue), pair(size, m)), templates());

        size.deactivate(CTX);
        assertInvalid(p, pair(color, blue), pair(size, m));
    }

    @Test
    @DisplayName("Mẫu không nằm trong dữ liệu đưa vào bị từ chối")
    void unknownTemplate() {
        assertThatThrownBy(() -> VariantCombinationPolicy.validate(twoAxes(),
                List.of(pair(color, red), pair(size, m)), Map.of()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(VariantErrorCode.VARIANT_COMBINATION_INVALID));
    }

    @Test
    @DisplayName("Sản phẩm không có trục: chỉ tổ hợp rỗng hợp lệ (biến thể mặc định)")
    void defaultVariantOnly() {
        VariantCombinationPolicy.validate(product(), List.of(), Map.of());
        Product noAxisButAttribute = product(ProductAttribute.ofOptions(color.getId(), false, List.of(red)));
        VariantCombinationPolicy.validate(noAxisButAttribute, List.of(), templates());
        assertInvalid(noAxisButAttribute, pair(color, red));
    }
}
