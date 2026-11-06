package vn.t3nexus.catalog.domain.category;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-01 cho AGG-CAT-01: cây ba cấp, lá, tên, gán thuộc tính (INV-CAT-01, 02, 03). */
class CategoryTest {

    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-0000000000aa");

    private static ChangeContext ctx() {
        return new ChangeContext(new CatalogActor(ADMIN, "ADMIN"), UUID.randomUUID(), NOW);
    }

    private static CategoryId newId() {
        return CategoryId.of(UUID.randomUUID());
    }

    private static Category root() {
        Category c = Category.createRoot(newId(), "Thời trang", 0, ctx());
        c.clearDomainEvents();
        return c;
    }

    private static Category child(Category parent) {
        Category c = Category.createChild(newId(), "Áo nam", parent.getId(), parent.getLevel(), 0, ctx());
        c.clearDomainEvents();
        return c;
    }

    private static Category leaf() {
        Category l1 = root();
        Category l2 = child(l1);
        return child(l2);
    }

    private static AttributeTemplateId templateId() {
        return AttributeTemplateId.of(UUID.randomUUID());
    }

    private static CategoryAttributeAssignment assignment(AttributeTemplateId id, int order) {
        return CategoryAttributeAssignment.create(id, InputType.TEXT, false, order, null);
    }

    private static CategoryChanged lastEvent(Category c) {
        List<DomainEvent> events = List.copyOf(c.getDomainEvents());
        return (CategoryChanged) events.get(events.size() - 1);
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(expected);
    }

    // ---- tạo, ba cấp ----

    @Test
    void createsRootActiveAtLevelOneWithTidyNameAndKey() {
        Category c = Category.createRoot(newId(), "  Điện   thoại ", 2, ctx());

        assertThat(c.getName()).isEqualTo("Điện thoại");
        assertThat(c.getNameKey()).isEqualTo("dien thoai");
        assertThat(c.getLevel()).isEqualTo(CategoryLevel.L1);
        assertThat(c.getParentId()).isNull();
        assertThat(c.getSortOrder()).isEqualTo(2);
        assertThat(c.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(c.version()).isNull();
        assertThat(c.getAssignments()).isEmpty();
    }

    @Test
    void creationEmitsEventWithVersionZeroNameAndLevel() {
        Category c = Category.createRoot(newId(), "Thời trang", 0, ctx());

        CategoryChanged event = lastEvent(c);
        assertThat(event.change()).isEqualTo(CategoryChanged.Change.CREATED);
        assertThat(event.version()).isZero();
        assertThat(event.actorId()).isEqualTo(ADMIN);
        assertThat(event.name()).isEqualTo("Thời trang");
        assertThat(event.level()).isEqualTo(1);
        assertThat(event.parentId()).isNull();
    }

    @Test
    void childLevelIsParentPlusOneAndRecordsParent() {
        Category l1 = root();
        Category l2 = Category.createChild(newId(), "Áo nam", l1.getId(), l1.getLevel(), 0, ctx());
        Category l3 = Category.createChild(newId(), "Áo thun", l2.getId(), l2.getLevel(), 0, ctx());

        assertThat(l2.getLevel()).isEqualTo(CategoryLevel.L2);
        assertThat(l2.getParentId()).isEqualTo(l1.getId());
        assertThat(l3.getLevel()).isEqualTo(CategoryLevel.L3);
        assertThat(lastEvent(l3).level()).isEqualTo(3);
        assertThat(lastEvent(l3).parentId()).isEqualTo(l2.getId().getValue());
    }

    /** INV-CAT-01: cây tối đa ba cấp, cấp thứ tư bị từ chối. */
    @Test
    void fourthLevelIsRejected() {
        Category l3 = leaf();

        assertCode(() -> Category.createChild(newId(), "Cấp bốn", l3.getId(), l3.getLevel(), 0, ctx()),
                CategoryErrorCode.CATEGORY_MAX_DEPTH_EXCEEDED);
    }

    @Test
    void onlyLevelThreeIsLeaf() {
        Category l1 = root();
        Category l2 = child(l1);
        Category l3 = child(l2);

        assertThat(l1.isLeaf()).isFalse();
        assertThat(l2.isLeaf()).isFalse();
        assertThat(l3.isLeaf()).isTrue();
    }

    @Test
    void rejectsBlankOrTooLongName() {
        for (String bad : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> Category.createRoot(newId(), bad, 0, ctx()))
                    .isInstanceOf(CatalogFieldException.class)
                    .extracting(e -> ((CatalogFieldException) e).field()).isEqualTo("name");
        }
        assertThatThrownBy(() -> Category.createRoot(newId(), "a".repeat(101), 0, ctx()))
                .isInstanceOf(CatalogFieldException.class);
        assertThat(Category.createRoot(newId(), "a".repeat(100), 0, ctx()).getName()).hasSize(100);
    }

    /** INV-CAT-02: khóa tên giống nhau với mọi cách viết cùng một tên. */
    @Test
    void nameKeyIgnoresCaseAccentsAndSpaces() {
        assertThat(Category.nameKeyOf(" ĐIỆN   thoại ")).isEqualTo(Category.nameKeyOf("dien thoai"));
        assertThat(Category.nameKeyOf("Áo nam")).isNotEqualTo(Category.nameKeyOf("Áo nữ"));
    }

    // ---- đổi tên, sắp xếp ----

    @Test
    void renameChangesNameAndKeyAndEmitsOldAndNewName() {
        Category c = root();

        c.rename("  Thời   trang nam ", ctx());

        assertThat(c.getName()).isEqualTo("Thời trang nam");
        assertThat(c.getNameKey()).isEqualTo("thoi trang nam");
        CategoryChanged event = lastEvent(c);
        assertThat(event.change()).isEqualTo(CategoryChanged.Change.RENAMED);
        assertThat(event.oldName()).isEqualTo("Thời trang");
        assertThat(event.name()).isEqualTo("Thời trang nam");
    }

    @Test
    void renameRejectsBlankAndKeepsOldName() {
        Category c = root();

        assertThatThrownBy(() -> c.rename(" ", ctx())).isInstanceOf(CatalogFieldException.class);

        assertThat(c.getName()).isEqualTo("Thời trang");
        assertThat(c.getNameKey()).isEqualTo("thoi trang");
        assertThat(c.getDomainEvents()).isEmpty();
    }

    @Test
    void renameAllowedWhenInactive() {
        Category c = root();
        c.deactivate(ctx());

        c.rename("Thời trang mới", ctx());

        assertThat(c.getName()).isEqualTo("Thời trang mới");
        assertThat(c.getStatus()).isEqualTo(CategoryStatus.INACTIVE);
    }

    @Test
    void reorderSetsPositionAndEmitsEventWithParent() {
        Category l1 = root();
        Category l2 = child(l1);

        l2.reorder(3, ctx());

        assertThat(l2.getSortOrder()).isEqualTo(3);
        assertThat(lastEvent(l2).change()).isEqualTo(CategoryChanged.Change.REORDERED);
        assertThat(lastEvent(l2).parentId()).isEqualTo(l1.getId().getValue());
    }

    // ---- ngừng, bật lại ----

    @Test
    void deactivateThenActivate() {
        Category c = root();

        c.deactivate(ctx());
        assertThat(c.isActive()).isFalse();
        assertThat(lastEvent(c).change()).isEqualTo(CategoryChanged.Change.DEACTIVATED);

        c.activate(ctx());
        assertThat(c.isActive()).isTrue();
        assertThat(lastEvent(c).change()).isEqualTo(CategoryChanged.Change.ACTIVATED);
        assertThat(c.getDomainEvents()).hasSize(2);
    }

    /** Q5: ngừng hay bật lại thứ đã ở trạng thái đó là 409, không đổi gì, không phát event. */
    @Test
    void settingTheSameStateIsRejectedWithoutEvent() {
        Category c = root();

        assertCode(() -> c.activate(ctx()), CatalogErrorCode.CATALOG_INVALID_STATE);
        c.deactivate(ctx());
        c.clearDomainEvents();
        assertCode(() -> c.deactivate(ctx()), CatalogErrorCode.CATALOG_INVALID_STATE);

        assertThat(c.getDomainEvents()).isEmpty();
        assertThat(c.getStatus()).isEqualTo(CategoryStatus.INACTIVE);
    }

    @Test
    void deactivatingDoesNotTouchAssignments() {
        Category c = leaf();
        c.replaceAssignments(List.of(assignment(templateId(), 0)), ctx());

        c.deactivate(ctx());

        assertThat(c.getAssignments()).hasSize(1);
    }

    // ---- gán thuộc tính ----

    /** INV-CAT-03: thuộc tính chỉ gán ở lá. */
    @Test
    void assignmentsOnlyOnLeaf() {
        Category l1 = root();
        Category l2 = child(l1);

        assertCode(() -> l1.replaceAssignments(List.of(assignment(templateId(), 0)), ctx()),
                CategoryErrorCode.CATEGORY_NOT_LEAF);
        assertCode(() -> l2.replaceAssignments(List.of(), ctx()), CategoryErrorCode.CATEGORY_NOT_LEAF);
        assertThat(l1.getAssignments()).isEmpty();
        assertThat(l1.getDomainEvents()).isEmpty();
    }

    @Test
    void replaceAssignmentsReplacesTheWholeListAndEmitsCount() {
        Category c = leaf();
        AttributeTemplateId a = templateId();
        AttributeTemplateId b = templateId();
        AttributeTemplateId d = templateId();
        c.replaceAssignments(List.of(assignment(a, 0), assignment(b, 1)), ctx());

        c.replaceAssignments(List.of(assignment(b, 0), assignment(d, 1)), ctx());

        assertThat(c.getAssignments()).extracting(CategoryAttributeAssignment::getAttributeTemplateId)
                .containsExactly(b, d);
        assertThat(lastEvent(c).change()).isEqualTo(CategoryChanged.Change.ASSIGNMENTS_REPLACED);
        assertThat(lastEvent(c).count()).isEqualTo(2);
    }

    @Test
    void emptyListRemovesEveryAssignment() {
        Category c = leaf();
        c.replaceAssignments(List.of(assignment(templateId(), 0)), ctx());

        c.replaceAssignments(List.of(), ctx());

        assertThat(c.getAssignments()).isEmpty();
        assertThat(lastEvent(c).count()).isZero();
    }

    /** INV-CAT-03: một mẫu chỉ gán một lần cho một danh mục. */
    @Test
    void duplicateTemplateIsRejectedAndKeepsOldList() {
        Category c = leaf();
        AttributeTemplateId kept = templateId();
        c.replaceAssignments(List.of(assignment(kept, 0)), ctx());
        c.clearDomainEvents();
        AttributeTemplateId dup = templateId();

        assertCode(() -> c.replaceAssignments(List.of(assignment(dup, 0), assignment(dup, 1)), ctx()),
                CategoryErrorCode.ATTRIBUTE_ASSIGNMENT_DUPLICATED);

        assertThat(c.getAssignments()).extracting(CategoryAttributeAssignment::getAttributeTemplateId)
                .containsExactly(kept);
        assertThat(c.getDomainEvents()).isEmpty();
    }

    @Test
    void assignmentsAreReadOnlyFromOutside() {
        Category c = leaf();
        c.replaceAssignments(List.of(assignment(templateId(), 0)), ctx());

        assertThatThrownBy(() -> c.getAssignments().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- ràng buộc theo kiểu nhập ----

    private static AttributeConstraints c(Integer maxLength, BigDecimal min, BigDecimal max, Boolean integerOnly,
                                          DatePrecision precision, String minDate, String maxDate, Integer maxSel) {
        return new AttributeConstraints(maxLength, min, max, integerOnly, precision, minDate, maxDate, maxSel);
    }

    private static void create(InputType type, AttributeConstraints constraints) {
        CategoryAttributeAssignment.create(templateId(), type, false, 0, constraints);
    }

    @Test
    void noConstraintsAreAlwaysAllowed() {
        for (InputType type : InputType.values()) {
            create(type, null);
            create(type, AttributeConstraints.NONE);
        }
    }

    @Test
    void textAllowsOnlyMaxLengthInRange() {
        create(InputType.TEXT, c(50, null, null, null, null, null, null, null));
        create(InputType.TEXT, c(255, null, null, null, null, null, null, null));
        for (AttributeConstraints bad : List.of(
                c(0, null, null, null, null, null, null, null),
                c(256, null, null, null, null, null, null, null),
                c(null, BigDecimal.ONE, null, null, null, null, null, null),
                c(null, null, null, true, null, null, null, null),
                c(null, null, null, null, null, null, null, 2))) {
            assertCode(() -> create(InputType.TEXT, bad), CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID);
        }
    }

    @Test
    void numberAllowsMinMaxIntegerOnlyWithMinNotAboveMax() {
        create(InputType.NUMBER, c(null, BigDecimal.ONE, BigDecimal.TEN, true, null, null, null, null));
        create(InputType.NUMBER, c(null, BigDecimal.TEN, BigDecimal.TEN, null, null, null, null, null));
        for (AttributeConstraints bad : List.of(
                c(null, BigDecimal.TEN, BigDecimal.ONE, null, null, null, null, null),
                c(5, null, null, null, null, null, null, null),
                c(null, null, null, null, DatePrecision.DAY, null, null, null),
                c(null, null, null, null, null, "2020-01-01", null, null))) {
            assertCode(() -> create(InputType.NUMBER, bad), CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID);
        }
    }

    @Test
    void multiSelectAllowsOnlyMaxSelectionsAtLeastOne() {
        create(InputType.MULTI_SELECT, c(null, null, null, null, null, null, null, 3));
        for (AttributeConstraints bad : List.of(
                c(null, null, null, null, null, null, null, 0),
                c(10, null, null, null, null, null, null, null),
                c(null, BigDecimal.ONE, null, null, null, null, null, null))) {
            assertCode(() -> create(InputType.MULTI_SELECT, bad), CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID);
        }
    }

    @Test
    void dateAllowsOnlyPrecisionAndDateBounds() {
        create(InputType.DATE, c(null, null, null, null, DatePrecision.MONTH, "2020-01", "2030-12", null));
        for (AttributeConstraints bad : List.of(
                c(10, null, null, null, null, null, null, null),
                c(null, BigDecimal.ONE, null, null, null, null, null, null),
                c(null, null, null, true, null, null, null, null),
                c(null, null, null, null, null, null, null, 2))) {
            assertCode(() -> create(InputType.DATE, bad), CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID);
        }
    }

    @Test
    void selectAndBooleanTakeNoConstraintsAtAll() {
        for (InputType type : new InputType[]{InputType.SINGLE_SELECT, InputType.BOOLEAN}) {
            assertCode(() -> create(type, c(10, null, null, null, null, null, null, null)),
                    CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID);
            assertCode(() -> create(type, c(null, null, null, null, null, null, null, 2)),
                    CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID);
        }
    }

    @Test
    void assignmentsAreEqualByTemplateOnly() {
        AttributeTemplateId id = templateId();
        CategoryAttributeAssignment a = CategoryAttributeAssignment.create(id, InputType.TEXT, true, 0, null);
        CategoryAttributeAssignment b = CategoryAttributeAssignment.create(id, InputType.TEXT, false, 9,
                c(10, null, null, null, null, null, null, null));

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(CategoryAttributeAssignment.create(templateId(), InputType.TEXT, true, 0, null));
    }

    // ---- xóa, sự kiện, phiên bản ----

    @Test
    void markDeletedEmitsEventWithNameAndParentBecauseNoPlaceToLookThemUpAfter() {
        Category l1 = root();
        Category l2 = child(l1);

        l2.markDeleted(ctx());

        CategoryChanged event = lastEvent(l2);
        assertThat(event.change()).isEqualTo(CategoryChanged.Change.DELETED);
        assertThat(event.name()).isEqualTo("Áo nam");
        assertThat(event.parentId()).isEqualTo(l1.getId().getValue());
    }

    @Test
    void eventVersionIsStoredVersionPlusOne() {
        Category stored = Category.reconstitute(newId(), "Thời trang", "thoi trang", null, CategoryLevel.L1, 0,
                CategoryStatus.ACTIVE, List.of(), 4L, NOW, NOW);

        stored.deactivate(ctx());

        assertThat(lastEvent(stored).version()).isEqualTo(5L);
        assertThat(stored.nextVersion()).isEqualTo(5L);
        assertThat(stored.version()).isEqualTo(4L);
    }

    @Test
    void newCategoryNextVersionIsZero() {
        assertThat(root().nextVersion()).isZero();
    }

    @Test
    void eventPayloadHasOnlyIdVersionChangeAndTime() {
        Category c = root();
        c.rename("Thời trang nam", ctx());

        assertThat(lastEvent(c).getRoutingKey()).isEqualTo("catalog.category.changed");
        assertThat(lastEvent(c).getAggregateId()).isEqualTo(c.getId().getValue().toString());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = (java.util.Map<String, Object>) lastEvent(c).getPayload();
        assertThat(payload.keySet()).containsExactly("categoryId", "version", "change", "occurredAt");
    }

    @Test
    void levelConversionRoundTripsAndRejectsOutOfRange() {
        for (CategoryLevel level : CategoryLevel.values()) {
            assertThat(CategoryLevel.of(level.getValue())).isEqualTo(level);
        }
        assertThatThrownBy(() -> CategoryLevel.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CategoryLevel.of(4)).isInstanceOf(IllegalArgumentException.class);
    }
}
