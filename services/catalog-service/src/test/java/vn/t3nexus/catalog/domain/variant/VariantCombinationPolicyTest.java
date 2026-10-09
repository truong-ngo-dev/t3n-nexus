package vn.t3nexus.catalog.domain.variant;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** INV-CAT-97 (tiên quyết) + INV-CAT-99 (liên tục) — tổ hợp phân loại của đơn vị bán được mới. */
class VariantCombinationPolicyTest {

    private static final AttributeTemplateId COLOR = AttributeTemplateId.of("01COLOR");
    private static final AttributeTemplateId SIZE = AttributeTemplateId.of("01SIZE");
    private static final AttributeTemplateId ORIGIN = AttributeTemplateId.of("01ORIGIN");

    private final AttributeTemplate color =
            AttributeTemplate.create(COLOR, "color", "Màu", null, InputType.SINGLE_SELECT, null);
    private final AttributeTemplate size =
            AttributeTemplate.create(SIZE, "size", "Size", null, InputType.SINGLE_SELECT, null);
    private final AttributeTemplate origin =
            AttributeTemplate.create(ORIGIN, "origin", "Xuất xứ", null, InputType.SINGLE_SELECT, null);

    private final Product product;

    VariantCombinationPolicyTest() {
        color.addOption(AttributeOptionId.of("red"), "red", "Đỏ");
        color.addOption(AttributeOptionId.of("blue"), "blue", "Xanh");
        color.addOption(AttributeOptionId.of("green"), "green", "Lục"); // Admin có, Product không khai
        size.addOption(AttributeOptionId.of("m"), "m", "M");
        origin.addOption(AttributeOptionId.of("jp"), "jp", "Nhật Bản");

        // Trục: Màu (đỏ, xanh) + Size (M). Thông số chung: Xuất xứ (Nhật Bản).
        product = Product.create(ProductId.of("01P"), "01SELLER", CategoryId.of("01C"), BrandId.of("01B"),
                "Áo", null, null, List.of(
                        new ProductAttributeValue(COLOR, List.of("red", "blue"), true),
                        new ProductAttributeValue(SIZE, List.of("m"), true),
                        new ProductAttributeValue(ORIGIN, List.of("jp"), false)));
    }

    private Map<String, AttributeTemplate> templates() {
        return Map.of("01COLOR", color, "01SIZE", size, "01ORIGIN", origin);
    }

    private static VariantAttributePair pair(AttributeTemplateId t, String option) {
        return new VariantAttributePair(t, AttributeOptionId.of(option));
    }

    private void assertCode(List<VariantAttributePair> pairs, Object code) {
        assertThatThrownBy(() -> VariantCombinationPolicy.validate(product, pairs, templates()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(code);
    }

    @Test
    void exactlyOneValuePerAxisIsValid() {
        assertThatCode(() -> VariantCombinationPolicy.validate(
                product, List.of(pair(COLOR, "red"), pair(SIZE, "m")), templates()))
                .doesNotThrowAnyException();
    }

    // ── INV-CAT-99: không thiếu, không thừa, không trùng trục ──

    @Test
    void missingAxisIsRejected() {
        assertCode(List.of(pair(COLOR, "red")), ProductErrorCode.VARIANT_MISSING_VARIANT_DEFINING_ATTRIBUTE);
    }

    @Test
    void twoValuesForSameAxisIsRejected() {
        assertCode(List.of(pair(COLOR, "red"), pair(COLOR, "blue"), pair(SIZE, "m")),
                ProductErrorCode.VARIANT_DUPLICATE_AXIS);
    }

    @Test
    void attributeThatIsNotAnAxisIsRejected() {
        assertCode(List.of(pair(COLOR, "red"), pair(SIZE, "m"), pair(ORIGIN, "jp")),
                ProductErrorCode.VARIANT_ATTRIBUTE_NOT_AXIS);
    }

    @Test
    void optionOutsideProductDeclaredSetIsRejected() {
        assertCode(List.of(pair(COLOR, "green"), pair(SIZE, "m")),
                ProductErrorCode.VARIANT_OPTION_NOT_DECLARED_BY_PRODUCT);
    }

    // ── INV-CAT-97: đang dùng được lúc tạo ──

    @Test
    void deactivatedOptionIsRejectedForNewVariant() {
        color.deactivateOption(AttributeOptionId.of("red"));

        assertCode(List.of(pair(COLOR, "red"), pair(SIZE, "m")), ProductErrorCode.INVALID_ATTRIBUTE_VALUE);
    }

    @Test
    void deactivatedTemplateIsRejectedForNewVariant() {
        size.deactivate();

        assertCode(List.of(pair(COLOR, "red"), pair(SIZE, "m")), AttributeTemplateErrorCode.TEMPLATE_INACTIVE);
    }

    @Test
    void unknownOptionIsRejected() {
        assertCode(List.of(pair(COLOR, "nope"), pair(SIZE, "m")), AttributeTemplateErrorCode.OPTION_NOT_FOUND);
    }
}
