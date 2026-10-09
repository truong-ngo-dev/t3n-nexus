package vn.t3nexus.catalog.domain.category;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AGG-CAT-03 — analysis.md §6.3. */
class CategoryTest {

    private static final CategoryId ROOT = CategoryId.of("01ROOT00000000000000000000");
    private static final CategoryId LEAF = CategoryId.of("01LEAF00000000000000000000");

    private static CategoryAttributeAssignment assignment(String templateId) {
        return CategoryAttributeAssignment.create(AttributeTemplateId.of(templateId), InputType.BOOLEAN,
                false, 0, null, null);
    }

    private static Category leaf() {
        return Category.createChild(LEAF, "Điện thoại", ROOT, CategoryLevel.L2, 0);
    }

    // ── Đường dẫn (T-10) ──

    @Test
    void slugDerivedFromVietnameseName() {
        assertThat(CategorySlug.from("Điện thoại & Phụ kiện")).isEqualTo("dien-thoai-phu-kien");
        assertThat(CategorySlug.from("  Đồ gia dụng  ")).isEqualTo("do-gia-dung");
    }

    @Test
    void slugFallsBackWhenNameHasNoLetters() {
        assertThat(CategorySlug.from("!!!")).isEqualTo("danh-muc");
    }

    @Test
    void slugCappedAtMaxLength() {
        assertThat(CategorySlug.from("a ".repeat(200))).hasSizeLessThanOrEqualTo(CategorySlug.MAX_LENGTH)
                .doesNotEndWith("-");
    }

    @Test
    void renameRegeneratesSlug() {
        Category category = leaf();

        category.update("Máy tính bảng", null);

        assertThat(category.getSlug()).isEqualTo("may-tinh-bang");
    }

    // ── Cây ──

    @Test
    void cannotCreateBelowLeaf() {
        assertThatThrownBy(() -> Category.createChild(CategoryId.of("01X"), "Con", LEAF, CategoryLevel.L3, 0))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(CategoryErrorCode.MAX_DEPTH_EXCEEDED);
    }

    // ── INV-CAT-033, INV-CAT-034 ──

    @Test
    void onlyLeafAcceptsAssignments() {
        Category root = Category.createRoot(ROOT, "Điện tử", 0);

        assertThatThrownBy(() -> root.replaceAssignments(List.of(assignment("01T1"))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(CategoryErrorCode.ATTRIBUTE_ASSIGNMENT_REQUIRES_LEAF);
    }

    @Test
    void attributeAssignedAtMostOnce() {
        Category category = leaf();

        assertThatThrownBy(() -> category.replaceAssignments(List.of(assignment("01T1"), assignment("01T1"))))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(CategoryErrorCode.ASSIGNMENT_ALREADY_EXISTS);
    }

    // ── EVT-CAT-031: mọi thay đổi cây/luật thuộc tính đều phát CategoryUpdated ──

    @Test
    void everyChangeRaisesCategoryUpdated() {
        Category category = leaf();
        assertThat(category.getDomainEvents()).hasSize(1); // tạo
        category.clearDomainEvents();

        category.update("Điện thoại di động", null);
        category.deactivate();
        category.activate();
        category.reorder(2);
        category.replaceAssignments(List.of(assignment("01T1")));
        category.markDeleted();

        assertThat(category.getDomainEvents()).hasSize(6).allMatch(e -> e instanceof CategoryUpdatedEvent);
    }
}
