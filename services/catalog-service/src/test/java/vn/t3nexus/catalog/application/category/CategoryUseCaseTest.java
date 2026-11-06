package vn.t3nexus.catalog.application.category;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.catalog.application.CatalogAuditActions;
import vn.t3nexus.catalog.application.attributetemplate.FakeAttributeTemplateRepository;
import vn.t3nexus.catalog.application.category.event.CategoryChangedAuditHandler;
import vn.t3nexus.catalog.application.category.event.CategoryChangedOutboxHandler;
import vn.t3nexus.catalog.application.product.FakeProductRepository;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplate;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateId;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateStatus;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
import vn.t3nexus.catalog.domain.category.AttributeConstraints;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryStatus;
import vn.t3nexus.catalog.infrastructure.crosscutting.config.AuditConfig;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * KT-03 cho danh mục: use case với kho giả. Thứ tự kiểm vai, kiểm sớm, lưu, event; event mang {@code version() + 1};
 * bản ghi kiểm toán và hộp thư đúng hành động, đúng dữ kiện. Dùng danh mục hành động THẬT của {@link AuditConfig} nên
 * khóa dữ kiện chưa khai báo làm test đỏ.
 */
class CategoryUseCaseTest {

    private static final CatalogActor ADMIN = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000aa"), "ADMIN");
    private static final CatalogActor USER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000bb"), "USER");

    private FakeCategoryRepository categories;
    private FakeProductRepository products;
    private FakeAttributeTemplateRepository templates;
    private OutboxEventStore outbox;
    private IdGenerator ids;
    private Clock clock;
    private EventDispatcher dispatcher;
    private CategoryUpdater updater;
    private CreateCategory create;
    private RenameCategory rename;
    private ReorderCategories reorder;
    private DeleteCategory delete;
    private ReplaceCategoryAttributes replace;

    @BeforeEach
    void setUp() {
        categories = new FakeCategoryRepository();
        products = new FakeProductRepository();
        templates = new FakeAttributeTemplateRepository();
        outbox = mock(OutboxEventStore.class);
        ids = new UuidV7IdGenerator();
        clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC);
        AuditRecorder recorder = new AuditRecorder(outbox, new AuditConfig().auditActionCatalog(), "catalog-service", clock, ids);
        dispatcher = new EventDispatcher(List.of(
                new CategoryChangedAuditHandler(recorder),
                new CategoryChangedOutboxHandler(outbox)));
        updater = new CategoryUpdater(categories, ids, clock, dispatcher);
        create = new CreateCategory(categories, ids, clock, dispatcher);
        rename = new RenameCategory(categories, ids, clock, dispatcher);
        reorder = new ReorderCategories(categories, ids, clock, dispatcher);
        delete = new DeleteCategory(categories, products, ids, clock, dispatcher);
        replace = new ReplaceCategoryAttributes(categories, templates, ids, clock, dispatcher);
    }

    // ---- hỗ trợ ----

    private CategoryView root(String name) {
        return create.handle(new CreateCategory.Command(ADMIN, name, null));
    }

    private CategoryView child(String name, CategoryView parent) {
        return create.handle(new CreateCategory.Command(ADMIN, name, parent.id()));
    }

    /** Một nhánh đủ ba cấp, trả về lá. */
    private CategoryView leaf() {
        return child("Áo thun", child("Áo nam", root("Thời trang")));
    }

    private AttributeTemplateId template(String name, InputType type, boolean active, String... optionValues) {
        ChangeContext ctx = new ChangeContext(ADMIN, ids.generate(), clock.instant());
        AttributeTemplate t = AttributeTemplate.create(AttributeTemplateId.of(ids.generate()), name, name, null, type, null, ctx);
        for (String value : optionValues) {
            t.addOption(AttributeOptionId.of(ids.generate()), value, value, ctx);
        }
        if (!active) {
            t.deactivate(ctx);
        }
        templates.save(t);
        return t.getId();
    }

    private static ReplaceCategoryAttributes.Item item(AttributeTemplateId id, boolean required, int order) {
        return new ReplaceCategoryAttributes.Item(id.getValue(), required, order, null);
    }

    private List<DomainEvent> stored() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox, org.mockito.Mockito.atLeast(0)).store(captor.capture());
        return captor.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(DomainEvent e) {
        return (Map<String, Object>) e.getPayload();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(DomainEvent audit) {
        return (Map<String, Object>) payload(audit).get("details");
    }

    private static void assertCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(expected);
    }

    // ---- tạo ----

    @Test
    void createRootSavesAndWritesAuditThenOutboxWithVersionZero() {
        CategoryView view = root("Thời trang");

        assertThat(view.version()).isZero();
        assertThat(view.level()).isEqualTo(1);
        assertThat(view.parentId()).isNull();
        assertThat(view.status()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(categories.stored(view.id())).isNotNull();
        List<DomainEvent> events = stored();
        assertThat(events).hasSize(2);
        // kiểm toán chạy trước hộp thư
        assertThat(payload(events.get(0)).get("action")).isEqualTo(CatalogAuditActions.CATEGORY_CREATED);
        assertThat(details(events.get(0))).containsEntry("version", 0L).containsEntry("name", "Thời trang")
                .containsEntry("level", 1).hasSize(3);
        // bản ghi kiểm toán trỏ đúng đích (loại CATEGORY, mã danh mục) và đúng người thực hiện
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) payload(events.get(0)).get("target");
        assertThat(target).containsEntry("type", CatalogAuditActions.TARGET_CATEGORY)
                .containsEntry("id", view.id().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> actor = (Map<String, Object>) payload(events.get(0)).get("actor");
        assertThat(actor).containsEntry("accountId", ADMIN.accountId().toString()).containsEntry("role", "ADMIN");
        assertThat(events.get(1).getRoutingKey()).isEqualTo("catalog.category.changed");
        assertThat(payload(events.get(1))).containsEntry("categoryId", view.id().toString())
                .containsEntry("version", 0L).containsEntry("change", "CREATED");
        assertThat(events.get(1).getAggregateId()).isEqualTo(view.id().toString());
    }

    @Test
    void createChildRecordsParentAndLevelInAudit() {
        CategoryView parent = root("Thời trang");

        CategoryView view = child("Áo nam", parent);

        assertThat(view.level()).isEqualTo(2);
        assertThat(view.parentId()).isEqualTo(parent.id());
        Map<String, Object> d = details(stored().get(2));
        assertThat(d).containsEntry("parentId", parent.id().toString()).containsEntry("level", 2)
                .containsEntry("name", "Áo nam").hasSize(4);
    }

    @Test
    void newCategoryGoesLastAmongSiblings() {
        CategoryView a = root("A");
        CategoryView b = root("B");
        CategoryView c = root("C");

        assertThat(a.sortOrder()).isZero();
        assertThat(b.sortOrder()).isEqualTo(1);
        assertThat(c.sortOrder()).isEqualTo(2);
        assertThat(child("A1", a).sortOrder()).isZero(); // anh em tính theo cha
    }

    @Test
    void nonAdminIsRejectedBeforeAnythingElse() {
        assertCode(() -> create.handle(new CreateCategory.Command(USER, "Thời trang", null)),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(categories.saveCount).isZero();
        verifyNoInteractions(outbox);
    }

    /** INV-CAT-02: tên trùng sau chuẩn hóa trong cùng cha bị từ chối sớm, không lưu, không ghi. */
    @Test
    void duplicateNameInSameParentIsRejectedEvenWithDifferentAccentAndCase() {
        CategoryView parent = root("Thời trang");
        child("Áo nam", parent);
        int records = stored().size();
        int saves = categories.saveCount;

        assertCode(() -> child("AO   NAM", parent), CategoryErrorCode.CATEGORY_NAME_TAKEN);

        assertThat(categories.saveCount).isEqualTo(saves);
        assertThat(stored()).hasSize(records);
    }

    @Test
    void duplicateRootNameIsRejectedButSameNameUnderAnotherParentIsAllowed() {
        CategoryView fashion = root("Thời trang");
        CategoryView tech = root("Điện tử");
        child("Phụ kiện", fashion);

        assertCode(() -> root("thoi trang"), CategoryErrorCode.CATEGORY_NAME_TAKEN);
        assertThat(child("Phụ kiện", tech).name()).isEqualTo("Phụ kiện");
    }

    @Test
    void fourthLevelIsRejected() {
        CategoryView leaf = leaf();

        assertCode(() -> child("Cấp bốn", leaf), CategoryErrorCode.CATEGORY_MAX_DEPTH_EXCEEDED);
    }

    @Test
    void unknownParentIsNotFound() {
        assertCode(() -> create.handle(new CreateCategory.Command(ADMIN, "Con", UUID.randomUUID())),
                CategoryErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void closedParentStillAcceptsChildren() {
        CategoryView parent = root("Thời trang");
        new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, parent.id()));

        assertThat(child("Áo nam", parent).status()).isEqualTo(CategoryStatus.ACTIVE);
    }

    // ---- đổi tên ----

    @Test
    void renameUpdatesNameAndAuditsOldAndNewNameWithVersionOne() {
        CategoryView created = root("Thời trang");

        CategoryView view = rename.handle(new RenameCategory.Command(ADMIN, created.id(), "Thời trang nam"));

        assertThat(view.name()).isEqualTo("Thời trang nam");
        assertThat(view.version()).isEqualTo(1L);
        DomainEvent audit = stored().get(2);
        assertThat(payload(audit).get("action")).isEqualTo(CatalogAuditActions.CATEGORY_UPDATED);
        assertThat(details(audit)).containsEntry("version", 1L).containsEntry("change", "RENAMED")
                .containsEntry("oldName", "Thời trang").containsEntry("newName", "Thời trang nam").hasSize(4);
        assertThat(payload(stored().get(3))).containsEntry("change", "RENAMED").containsEntry("version", 1L);
    }

    @Test
    void renameToASiblingsNameIsRejectedButToOwnSpellingIsAllowed() {
        CategoryView parent = root("Thời trang");
        CategoryView nam = child("Áo nam", parent);
        child("Áo nữ", parent);

        assertCode(() -> rename.handle(new RenameCategory.Command(ADMIN, nam.id(), "ao nu")),
                CategoryErrorCode.CATEGORY_NAME_TAKEN);

        assertThat(rename.handle(new RenameCategory.Command(ADMIN, nam.id(), "ÁO NAM")).name()).isEqualTo("ÁO NAM");
    }

    @Test
    void renameToANameUsedUnderAnotherParentIsAllowed() {
        CategoryView fashion = root("Thời trang");
        CategoryView tech = root("Điện tử");
        child("Phụ kiện", fashion);
        CategoryView other = child("Linh kiện", tech);

        assertThat(rename.handle(new RenameCategory.Command(ADMIN, other.id(), "Phụ kiện")).name())
                .isEqualTo("Phụ kiện");
    }

    @Test
    void renameChecksRoleBeforeLookingUpAndReportsUnknown() {
        assertCode(() -> rename.handle(new RenameCategory.Command(USER, UUID.randomUUID(), "X")),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        assertCode(() -> rename.handle(new RenameCategory.Command(ADMIN, UUID.randomUUID(), "X")),
                CategoryErrorCode.CATEGORY_NOT_FOUND);
    }

    // ---- sắp xếp ----

    @Test
    void reorderWritesOnlyMovedCategoriesAndReturnsNewOrder() {
        CategoryView a = root("A");
        CategoryView b = root("B");
        CategoryView c = root("C");
        int records = stored().size();

        List<CategoryView> result = reorder.handle(new ReorderCategories.Command(
                ADMIN, null, List.of(a.id(), c.id(), b.id())));

        assertThat(result).extracting(CategoryView::id).containsExactly(a.id(), c.id(), b.id());
        assertThat(result).extracting(CategoryView::sortOrder).containsExactly(0, 1, 2);
        assertThat(result).extracting(CategoryView::version).containsExactly(0L, 1L, 1L); // a không đổi chỗ
        List<DomainEvent> events = stored().subList(records, stored().size());
        assertThat(events).hasSize(4); // hai danh mục đổi chỗ, mỗi cái kiểm toán rồi hộp thư
        assertThat(payload(events.get(0)).get("action")).isEqualTo(CatalogAuditActions.CATEGORY_UPDATED);
        assertThat(details(events.get(0))).containsEntry("change", "REORDERED").containsEntry("version", 1L).hasSize(2);
    }

    @Test
    void reorderUnderAParentAuditsParentId() {
        CategoryView parent = root("Thời trang");
        CategoryView x = child("X", parent);
        CategoryView y = child("Y", parent);
        int records = stored().size();

        reorder.handle(new ReorderCategories.Command(ADMIN, parent.id(), List.of(y.id(), x.id())));

        assertThat(details(stored().get(records))).containsEntry("parentId", parent.id().toString());
    }

    @Test
    void reorderWithSameOrderWritesNothing() {
        CategoryView a = root("A");
        CategoryView b = root("B");
        int records = stored().size();
        int saves = categories.saveCount;

        List<CategoryView> result = reorder.handle(new ReorderCategories.Command(ADMIN, null, List.of(a.id(), b.id())));

        assertThat(result).hasSize(2);
        assertThat(categories.saveCount).isEqualTo(saves);
        assertThat(stored()).hasSize(records);
    }

    @Test
    void reorderRejectsMissingExtraOrRepeatedSibling() {
        CategoryView a = root("A");
        CategoryView b = root("B");
        CategoryView other = child("Con", a);
        int saves = categories.saveCount;

        assertCode(() -> reorder.handle(new ReorderCategories.Command(ADMIN, null, List.of(a.id()))),
                CategoryErrorCode.CATEGORY_INVALID_ORDER); // thiếu
        assertCode(() -> reorder.handle(new ReorderCategories.Command(ADMIN, null, List.of(a.id(), b.id(), other.id()))),
                CategoryErrorCode.CATEGORY_INVALID_ORDER); // thừa (không cùng cha)
        assertCode(() -> reorder.handle(new ReorderCategories.Command(ADMIN, null, List.of(a.id(), a.id()))),
                CategoryErrorCode.CATEGORY_INVALID_ORDER); // lặp
        assertCode(() -> reorder.handle(new ReorderCategories.Command(ADMIN, null, List.of(a.id(), b.id(), a.id()))),
                CategoryErrorCode.CATEGORY_INVALID_ORDER); // lặp mà đủ tập

        assertThat(categories.saveCount).isEqualTo(saves);
    }

    @Test
    void reorderUnknownParentIsNotFoundAndNonAdminIsRejected() {
        assertCode(() -> reorder.handle(new ReorderCategories.Command(ADMIN, UUID.randomUUID(), List.of())),
                CategoryErrorCode.CATEGORY_NOT_FOUND);
        assertCode(() -> reorder.handle(new ReorderCategories.Command(USER, null, List.of())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
    }

    // ---- ngừng, bật lại ----

    @Test
    void deactivateAuditsOnlyVersionAndActivateAuditsUpdatedWithChange() {
        CategoryView created = root("Thời trang");

        CategoryView off = new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, created.id()));
        CategoryView on = new ActivateCategory(updater).handle(new ActivateCategory.Command(ADMIN, created.id()));

        assertThat(off.status()).isEqualTo(CategoryStatus.INACTIVE);
        assertThat(off.version()).isEqualTo(1L);
        assertThat(on.status()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(on.version()).isEqualTo(2L);
        List<DomainEvent> events = stored();
        assertThat(payload(events.get(2)).get("action")).isEqualTo(CatalogAuditActions.CATEGORY_DEACTIVATED);
        assertThat(details(events.get(2))).containsEntry("version", 1L).hasSize(1);
        assertThat(payload(events.get(4)).get("action")).isEqualTo(CatalogAuditActions.CATEGORY_UPDATED);
        assertThat(details(events.get(4))).containsEntry("change", "ACTIVATED").containsEntry("version", 2L).hasSize(2);
    }

    @Test
    void deactivatingChildlessParentDoesNotChangeChildren() {
        CategoryView parent = root("Thời trang");
        CategoryView kid = child("Áo nam", parent);

        new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, parent.id()));

        assertThat(categories.stored(kid.id()).getStatus()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(categories.stored(kid.id()).version()).isZero();
    }

    @Test
    void deactivatingTwiceIsInvalidStateWithoutExtraRecord() {
        CategoryView created = root("Thời trang");
        new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, created.id()));
        int records = stored().size();
        int saves = categories.saveCount;

        assertCode(() -> new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, created.id())),
                CatalogErrorCode.CATALOG_INVALID_STATE);

        assertThat(stored()).hasSize(records);
        assertThat(categories.saveCount).isEqualTo(saves);
    }

    @Test
    void nonAdminCannotChangeAnExistingCategory() {
        CategoryView created = root("Thời trang");
        int records = stored().size();

        assertCode(() -> new DeactivateCategory(updater).handle(new DeactivateCategory.Command(USER, created.id())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(stored()).hasSize(records);
        assertThat(categories.stored(created.id()).version()).isZero();
    }

    @Test
    void unknownCategoryIsNotFoundOnStatusChange() {
        assertCode(() -> new ActivateCategory(updater).handle(new ActivateCategory.Command(ADMIN, UUID.randomUUID())),
                CategoryErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void auditFailureAbortsTheUseCaseSoCallerSeesTheError() {
        CategoryView created = root("Thời trang");
        doThrow(new IllegalStateException("audit down")).when(outbox).store(any());

        assertThatThrownBy(() -> rename.handle(new RenameCategory.Command(ADMIN, created.id(), "Mới")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- xóa ----

    /** INV-CAT-05: danh mục còn con không bị xóa. */
    @Test
    void deleteWithChildrenIsRejectedAndKeepsEverything() {
        CategoryView parent = root("Thời trang");
        child("Áo nam", parent);
        int records = stored().size();

        assertCode(() -> delete.handle(new DeleteCategory.Command(ADMIN, parent.id())),
                CategoryErrorCode.CATEGORY_HAS_CHILDREN);

        assertThat(categories.stored(parent.id())).isNotNull();
        assertThat(categories.deleteCount).isZero();
        assertThat(stored()).hasSize(records);
    }

    @Test
    void deleteChildlessCategoryAuditsNameAndParentAndWritesOutbox() {
        CategoryView parent = root("Thời trang");
        CategoryView kid = child("Áo nam", parent);
        int records = stored().size();

        delete.handle(new DeleteCategory.Command(ADMIN, kid.id()));

        assertThat(categories.stored(kid.id())).isNull();
        assertThat(categories.stored(parent.id())).isNotNull();
        List<DomainEvent> events = stored().subList(records, stored().size());
        assertThat(events).hasSize(2);
        assertThat(payload(events.get(0)).get("action")).isEqualTo(CatalogAuditActions.CATEGORY_DELETED);
        assertThat(details(events.get(0))).containsEntry("name", "Áo nam")
                .containsEntry("parentId", parent.id().toString()).containsEntry("version", 1L).hasSize(3);
        assertThat(payload(events.get(1))).containsEntry("change", "DELETED");
    }

    @Test
    void deleteRootAuditsNoParent() {
        CategoryView root = root("Thời trang");
        int records = stored().size();

        delete.handle(new DeleteCategory.Command(ADMIN, root.id()));

        assertThat(details(stored().get(records))).containsEntry("name", "Thời trang").doesNotContainKey("parentId");
    }

    @Test
    void deleteChecksRoleAndReportsUnknown() {
        CategoryView root = root("Thời trang");

        assertCode(() -> delete.handle(new DeleteCategory.Command(USER, root.id())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        assertCode(() -> delete.handle(new DeleteCategory.Command(ADMIN, UUID.randomUUID())),
                CategoryErrorCode.CATEGORY_NOT_FOUND);
        assertThat(categories.stored(root.id())).isNotNull();
    }

    @Test
    void deleteCategoryWithReferencingProductsIsRejected() {
        CategoryView leaf = leaf();
        products.categoriesInUse.add(leaf.id());
        int records = stored().size();

        assertCode(() -> delete.handle(new DeleteCategory.Command(ADMIN, leaf.id())),
                CategoryErrorCode.CATEGORY_HAS_PRODUCTS);

        assertThat(categories.stored(leaf.id())).isNotNull();
        assertThat(categories.findById(vn.t3nexus.catalog.domain.category.CategoryId.of(leaf.id()))).isPresent();
        assertThat(categories.deleteCount).isZero();
        assertThat(stored()).hasSize(records);
    }

    @Test
    void deleteCategoryWithChildrenAndReferencingProductsReportsChildrenFirst() {
        CategoryView parent = root("Thời trang");
        child("Áo nam", parent);
        products.categoriesInUse.add(parent.id());
        int records = stored().size();

        assertCode(() -> delete.handle(new DeleteCategory.Command(ADMIN, parent.id())),
                CategoryErrorCode.CATEGORY_HAS_CHILDREN);

        assertThat(categories.stored(parent.id())).isNotNull();
        assertThat(categories.deleteCount).isZero();
        assertThat(stored()).hasSize(records);
    }

    // ---- gán thuộc tính ----

    @Test
    void replaceAssignsTemplatesAndAuditsCountWithVersionOne() {
        CategoryView leaf = leaf();
        AttributeTemplateId color = template("color", InputType.SINGLE_SELECT, true, "red");
        AttributeTemplateId weight = template("weight", InputType.NUMBER, true);
        int records = stored().size();

        List<AssignmentView> result = replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(),
                List.of(item(weight, false, 1), item(color, true, 0))));

        assertThat(result).extracting(AssignmentView::name).containsExactly("color", "weight"); // theo thứ tự hiển thị
        assertThat(result.get(0).required()).isTrue();
        assertThat(categories.stored(leaf.id()).getAssignments()).hasSize(2);
        assertThat(categories.stored(leaf.id()).version()).isEqualTo(1L);
        DomainEvent audit = stored().get(records);
        assertThat(payload(audit).get("action")).isEqualTo(CatalogAuditActions.CATEGORY_UPDATED);
        assertThat(details(audit)).containsEntry("change", "ASSIGNMENTS_REPLACED").containsEntry("count", 2)
                .containsEntry("version", 1L).hasSize(3);
    }

    @Test
    void replaceOnNonLeafIsRejected() {
        CategoryView l1 = root("Thời trang");
        AttributeTemplateId color = template("color", InputType.TEXT, true);

        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, l1.id(), List.of(item(color, false, 0)))),
                CategoryErrorCode.CATEGORY_NOT_LEAF);
    }

    @Test
    void replaceWithUnknownTemplateIsNotFound() {
        CategoryView leaf = leaf();

        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(),
                        List.of(new ReplaceCategoryAttributes.Item(UUID.randomUUID(), false, 0, null)))),
                AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NOT_FOUND);
    }

    /** INV-CAT-03: mẫu MỚI thêm phải dùng được; ngừng dùng hay kiểu chọn không còn tùy chọn đều không dùng được. */
    @Test
    void newlyAddedTemplateMustBeUsable() {
        CategoryView leaf = leaf();
        AttributeTemplateId inactive = template("old", InputType.TEXT, false);
        AttributeTemplateId noOption = template("empty_select", InputType.SINGLE_SELECT, true);

        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(),
                List.of(item(inactive, false, 0)))), AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_INACTIVE);
        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(),
                List.of(item(noOption, false, 0)))), AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_INACTIVE);
        assertThat(categories.stored(leaf.id()).getAssignments()).isEmpty();
    }

    /** Mẫu đã gán từ trước mà nay ngừng dùng vẫn giữ và sửa được; chỉ cái mới thêm mới bị kiểm. */
    @Test
    void templateAlreadyAssignedStaysEvenAfterItWasDeactivated() {
        CategoryView leaf = leaf();
        ChangeContext ctx = new ChangeContext(ADMIN, ids.generate(), clock.instant());
        AttributeTemplateId id = template("old", InputType.TEXT, true);
        replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(), List.of(item(id, false, 0))));
        AttributeTemplate stored = templates.stored(id.getValue());
        stored.deactivate(ctx);
        templates.save(stored);

        List<AssignmentView> result = replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(),
                List.of(item(id, true, 5))));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).required()).isTrue();
        assertThat(result.get(0).usable()).isFalse();
        assertThat(result.get(0).templateStatus()).isEqualTo(AttributeTemplateStatus.INACTIVE);
    }

    @Test
    void duplicateTemplateInRequestIsRejected() {
        CategoryView leaf = leaf();
        AttributeTemplateId id = template("color", InputType.TEXT, true);

        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(),
                List.of(item(id, false, 0), item(id, true, 1)))), CategoryErrorCode.ATTRIBUTE_ASSIGNMENT_DUPLICATED);
        assertThat(categories.stored(leaf.id()).getAssignments()).isEmpty();
    }

    @Test
    void constraintsMustFitTheTemplateInputType() {
        CategoryView leaf = leaf();
        AttributeTemplateId text = template("note", InputType.TEXT, true);
        AttributeTemplateId number = template("weight", InputType.NUMBER, true);
        AttributeConstraints maxLength = new AttributeConstraints(50, null, null, null, null, null, null, null);
        AttributeConstraints range = new AttributeConstraints(null, BigDecimal.ONE, BigDecimal.TEN, true, null, null, null, null);

        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(), List.of(
                        new ReplaceCategoryAttributes.Item(number.getValue(), false, 0, maxLength)))),
                CategoryErrorCode.ATTRIBUTE_CONSTRAINTS_INVALID);

        List<AssignmentView> ok = replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(), List.of(
                new ReplaceCategoryAttributes.Item(text.getValue(), false, 0, maxLength),
                new ReplaceCategoryAttributes.Item(number.getValue(), false, 1, range))));
        assertThat(ok.get(0).constraints()).isEqualTo(maxLength);
        assertThat(ok.get(1).constraints()).isEqualTo(range);
    }

    @Test
    void emptyListClearsAssignments() {
        CategoryView leaf = leaf();
        AttributeTemplateId id = template("color", InputType.TEXT, true);
        replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(), List.of(item(id, false, 0))));

        List<AssignmentView> result = replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(), List.of()));

        assertThat(result).isEmpty();
        assertThat(categories.stored(leaf.id()).getAssignments()).isEmpty();
    }

    @Test
    void replaceChecksRoleAndReportsUnknownCategory() {
        CategoryView leaf = leaf();

        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(USER, leaf.id(), List.of())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        assertCode(() -> replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, UUID.randomUUID(), List.of())),
                CategoryErrorCode.CATEGORY_NOT_FOUND);
    }

    // ---- đọc ----

    @Test
    void publicTreeShowsOnlyUsableBranchesInOrder() {
        CategoryView fashion = root("Thời trang");
        CategoryView tech = root("Điện tử");
        CategoryView men = child("Áo nam", fashion);
        child("Áo thun", men);
        CategoryView phone = child("Điện thoại", tech);
        child("Smartphone", phone);
        new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, phone.id()));
        new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, fashion.id()));

        List<GetCategoryTree.Node> tree = new GetCategoryTree(categories).handle(new GetCategoryTree.Query());

        // nhánh Thời trang bị ẩn cả nhánh con; trong Điện tử, Điện thoại ngừng nên cả Smartphone cũng ẩn
        assertThat(tree).extracting(GetCategoryTree.Node::name).containsExactly("Điện tử");
        assertThat(tree.get(0).children()).isEmpty();
    }

    @Test
    void publicTreeIsSortedBySortOrderAndNestedByLevel() {
        CategoryView a = root("A");
        CategoryView b = root("B");
        CategoryView a1 = child("A1", a);
        child("A1x", a1);
        reorder.handle(new ReorderCategories.Command(ADMIN, null, List.of(b.id(), a.id())));

        List<GetCategoryTree.Node> tree = new GetCategoryTree(categories).handle(new GetCategoryTree.Query());

        assertThat(tree).extracting(GetCategoryTree.Node::name).containsExactly("B", "A");
        assertThat(tree.get(1).children().get(0).children().get(0).level()).isEqualTo(3);
    }

    @Test
    void adminTreeShowsEverythingWithUsableFlagThroughAncestors() {
        CategoryView fashion = root("Thời trang");
        CategoryView men = child("Áo nam", fashion);
        CategoryView shirt = child("Áo thun", men);
        new DeactivateCategory(updater).handle(new DeactivateCategory.Command(ADMIN, fashion.id()));

        List<GetAdminCategoryTree.Node> tree = new GetAdminCategoryTree(categories)
                .handle(new GetAdminCategoryTree.Query(ADMIN));

        GetAdminCategoryTree.Node l1 = tree.get(0);
        GetAdminCategoryTree.Node l2 = l1.children().get(0);
        GetAdminCategoryTree.Node l3 = l2.children().get(0);
        assertThat(l1.status()).isEqualTo(CategoryStatus.INACTIVE);
        assertThat(l1.usable()).isFalse();
        assertThat(l2.status()).isEqualTo(CategoryStatus.ACTIVE); // ngừng không cascade
        assertThat(l2.usable()).isFalse(); // nhưng không dùng được vì cha đóng
        assertThat(l3.usable()).isFalse();
        assertThat(l3.id()).isEqualTo(shirt.id());
        assertThat(l1.version()).isEqualTo(1L);
    }

    @Test
    void adminTreeMarksOpenBranchUsable() {
        CategoryView leaf = leaf();

        List<GetAdminCategoryTree.Node> tree = new GetAdminCategoryTree(categories)
                .handle(new GetAdminCategoryTree.Query(ADMIN));

        assertThat(tree.get(0).children().get(0).children().get(0).id()).isEqualTo(leaf.id());
        assertThat(tree.get(0).usable()).isTrue();
        assertThat(tree.get(0).children().get(0).children().get(0).usable()).isTrue();
    }

    @Test
    void adminReadsRequireAdmin() {
        CategoryView leaf = leaf();

        assertCode(() -> new GetAdminCategoryTree(categories).handle(new GetAdminCategoryTree.Query(USER)),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        assertCode(() -> new GetCategoryAssignments(categories, templates)
                        .handle(new GetCategoryAssignments.Query(USER, leaf.id())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
    }

    @Test
    void sellerAttributesShowOnlyUsableTemplatesWithActiveOptionsInOrder() {
        CategoryView leaf = leaf();
        AttributeTemplateId color = template("color", InputType.SINGLE_SELECT, true, "red", "blue", "green");
        AttributeTemplateId note = template("note", InputType.TEXT, true);
        AttributeTemplateId retired = template("retired", InputType.TEXT, true);
        replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(),
                List.of(item(note, false, 1), item(color, true, 0), item(retired, false, 2))));
        AttributeTemplate c = templates.stored(color.getValue());
        ChangeContext ctx = new ChangeContext(ADMIN, ids.generate(), clock.instant());
        c.deactivateOption(c.getOptionsInOrder().get(1).getId(), ctx); // blue
        templates.save(c);
        AttributeTemplate r = templates.stored(retired.getValue());
        r.deactivate(ctx);
        templates.save(r);

        List<GetCategoryAttributes.Attribute> attributes = new GetCategoryAttributes(categories, templates)
                .handle(new GetCategoryAttributes.Query(leaf.id()));

        assertThat(attributes).extracting(GetCategoryAttributes.Attribute::name).containsExactly("color", "note");
        assertThat(attributes.get(0).required()).isTrue();
        assertThat(attributes.get(0).options()).extracting(GetCategoryAttributes.Option::value)
                .containsExactly("red", "green");
    }

    @Test
    void sellerAttributesOfNonLeafAreEmptyAndUnknownIsNotFound() {
        CategoryView l1 = root("Thời trang");

        assertThat(new GetCategoryAttributes(categories, templates).handle(new GetCategoryAttributes.Query(l1.id())))
                .isEmpty();
        assertCode(() -> new GetCategoryAttributes(categories, templates)
                        .handle(new GetCategoryAttributes.Query(UUID.randomUUID())),
                CategoryErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void adminAssignmentsIncludeInactiveTemplates() {
        CategoryView leaf = leaf();
        AttributeTemplateId id = template("old", InputType.TEXT, true);
        replace.handle(new ReplaceCategoryAttributes.Command(ADMIN, leaf.id(), List.of(item(id, false, 0))));
        AttributeTemplate t = templates.stored(id.getValue());
        t.deactivate(new ChangeContext(ADMIN, ids.generate(), clock.instant()));
        templates.save(t);

        List<AssignmentView> result = new GetCategoryAssignments(categories, templates)
                .handle(new GetCategoryAssignments.Query(ADMIN, leaf.id()));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).usable()).isFalse();
        assertThat(new GetCategoryAttributes(categories, templates).handle(new GetCategoryAttributes.Query(leaf.id())))
                .isEmpty();
    }
}
