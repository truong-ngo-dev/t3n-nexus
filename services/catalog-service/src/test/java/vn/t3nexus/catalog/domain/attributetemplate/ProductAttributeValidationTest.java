package vn.t3nexus.catalog.domain.attributetemplate;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryAttributeAssignment;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryLevel;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.ProductAttributeValue;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** INV-CAT-91, INV-CAT-92 + ràng buộc riêng của danh mục, trục phân loại, thuộc tính đã gỡ khỏi danh mục. */
class ProductAttributeValidationTest {

    private static final CategoryId LEAF = CategoryId.of("01LEAF");
    private static final AttributeTemplateId BATTERY = AttributeTemplateId.of("01BATTERY");
    private static final AttributeTemplateId TAGS = AttributeTemplateId.of("01TAGS");
    private static final AttributeTemplateId REMOVED = AttributeTemplateId.of("01REMOVED");

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final AttributeTemplateRepository templateRepository = mock(AttributeTemplateRepository.class);
    private final AttributeTemplateDomainService service =
            new AttributeTemplateDomainService(categoryRepository, templateRepository);

    private final AttributeTemplate battery =
            AttributeTemplate.create(BATTERY, "battery", "Dung lượng pin", null, InputType.NUMBER, "mAh");
    private final AttributeTemplate tags =
            AttributeTemplate.create(TAGS, "tags", "Nhãn", null, InputType.MULTI_SELECT, null);
    private final AttributeTemplate removed =
            AttributeTemplate.create(REMOVED, "origin", "Xuất xứ", null, InputType.TEXT, null);

    ProductAttributeValidationTest() {
        tags.addOption(AttributeOptionId.of("t1"), "a", "A");
        tags.addOption(AttributeOptionId.of("t2"), "b", "B");
        tags.addOption(AttributeOptionId.of("t3"), "c", "C");

        Category leaf = Category.createChild(LEAF, "Điện thoại", CategoryId.of("01L2"), CategoryLevel.L2, 0);
        leaf.replaceAssignments(List.of(
                CategoryAttributeAssignment.create(BATTERY, InputType.NUMBER, false, 0,
                        new AttributeConstraints(null, BigDecimal.ZERO, new BigDecimal("6000"), true,
                                null, null, null, null), null),
                CategoryAttributeAssignment.create(TAGS, InputType.MULTI_SELECT, false, 1,
                        new AttributeConstraints(null, null, null, null, null, null, null, 2), null)));
        when(categoryRepository.findById(LEAF)).thenReturn(Optional.of(leaf));
        when(templateRepository.findAllByIds(any())).thenReturn(List.of(battery, tags, removed));
    }

    private static ProductAttributeValue value(AttributeTemplateId id, String... values) {
        return new ProductAttributeValue(id, List.of(values), false);
    }

    private void assertCode(List<ProductAttributeValue> submitted, List<ProductAttributeValue> previous,
                            ProductErrorCode code) {
        assertThatThrownBy(() -> service.validateProductAttributes(LEAF, submitted, previous))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(code);
    }

    // ── Ràng buộc riêng của danh mục ──

    @Test
    void newNumberAboveCategoryMaxIsRejected() {
        assertCode(List.of(value(BATTERY, "8000")), List.of(), ProductErrorCode.ATTRIBUTE_VALUE_CONSTRAINT_VIOLATED);
    }

    @Test
    void integerOnlyRejectsDecimal() {
        assertCode(List.of(value(BATTERY, "4000.5")), List.of(), ProductErrorCode.ATTRIBUTE_VALUE_CONSTRAINT_VIOLATED);
    }

    /** INV-CAT-92 — Admin siết max sau khi seller đã lưu 8000: giữ nguyên thì vẫn hợp lệ. */
    @Test
    void unchangedValueOutsideTightenedConstraintIsExempt() {
        assertThatCode(() -> service.validateProductAttributes(
                LEAF, List.of(value(BATTERY, "8000")), List.of(value(BATTERY, "8000"))))
                .doesNotThrowAnyException();
    }

    @Test
    void multiSelectAboveMaxSelectionsIsRejected() {
        assertCode(List.of(value(TAGS, "t1", "t2", "t3")), List.of(), ProductErrorCode.ATTRIBUTE_VALUE_CONSTRAINT_VIOLATED);
    }

    // ── Trục phân loại chỉ kiểu "chọn 1" ──

    @Test
    void multiSelectCannotBeVariantDefining() {
        assertCode(List.of(new ProductAttributeValue(TAGS, List.of("t1"), true)), List.of(),
                ProductErrorCode.VARIANT_DEFINING_REQUIRES_SELECT);
    }

    // ── Thuộc tính đã gỡ khỏi danh mục: giữ nguyên hoặc bỏ hẳn ──

    @Test
    void removedAttributeCanBeKept() {
        assertThatCode(() -> service.validateProductAttributes(
                LEAF, List.of(value(REMOVED, "Nhật Bản")), List.of(value(REMOVED, "Nhật Bản"))))
                .doesNotThrowAnyException();
    }

    @Test
    void removedAttributeCannotReceiveNewValue() {
        assertCode(List.of(value(REMOVED, "Hàn Quốc")), List.of(value(REMOVED, "Nhật Bản")),
                ProductErrorCode.ATTRIBUTE_NOT_IN_CATEGORY);
    }

    @Test
    void removedAttributeCanBeDropped() {
        assertThatCode(() -> service.validateProductAttributes(
                LEAF, List.of(), List.of(value(REMOVED, "Nhật Bản"))))
                .doesNotThrowAnyException();
    }
}
