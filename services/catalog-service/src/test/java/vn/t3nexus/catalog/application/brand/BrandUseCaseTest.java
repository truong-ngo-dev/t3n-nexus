package vn.t3nexus.catalog.application.brand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.catalog.application.CatalogAuditActions;
import vn.t3nexus.catalog.application.brand.event.BrandChangedAuditHandler;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.catalog.infrastructure.crosscutting.config.AuditConfig;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.exception.ErrorCode;
import vn.t3nexus.lib.common.domain.model.DomainEvent;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.common.domain.service.UuidV7IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;

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
 * KT-03 cho thương hiệu: use case với kho giả. Thứ tự kiểm vai, kiểm sớm, lưu, event; event mang
 * {@code version() + 1}; bản ghi kiểm toán đúng hành động, đúng dữ kiện, và không có hàng hộp thư nào khác. Dùng danh
 * mục hành động THẬT của {@link AuditConfig} nên khóa dữ kiện chưa khai báo làm test đỏ.
 */
class BrandUseCaseTest {

    private static final CatalogActor ADMIN = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000aa"), "ADMIN");
    private static final CatalogActor USER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000bb"), "USER");

    private FakeBrandRepository repository;
    private OutboxEventStore outbox;
    private BrandUpdater updater;
    private CreateBrand create;
    private RenameBrand rename;

    @BeforeEach
    void setUp() {
        repository = new FakeBrandRepository();
        outbox = mock(OutboxEventStore.class);
        IdGenerator ids = new UuidV7IdGenerator();
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC);
        AuditRecorder recorder = new AuditRecorder(outbox, new AuditConfig().auditActionCatalog(), "catalog-service", clock, ids);
        EventDispatcher dispatcher = new EventDispatcher(List.of(new BrandChangedAuditHandler(recorder)));
        updater = new BrandUpdater(repository, ids, clock, dispatcher);
        create = new CreateBrand(repository, ids, clock, dispatcher);
        rename = new RenameBrand(repository, ids, clock, dispatcher);
    }

    private BrandView createBrand(String name) {
        return create.handle(new CreateBrand.Command(ADMIN, name));
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
    void createSavesBrandAndWritesOnlyAnAuditRecordWithVersionZero() {
        BrandView view = createBrand("Apple");

        assertThat(view.version()).isZero();
        assertThat(view.status()).isEqualTo(BrandStatus.ACTIVE);
        assertThat(repository.stored(view.id())).isNotNull();
        List<DomainEvent> events = stored();
        assertThat(events).hasSize(1); // thương hiệu không ra hộp thư riêng, chỉ bản ghi kiểm toán
        assertThat(payload(events.get(0)).get("action")).isEqualTo(CatalogAuditActions.BRAND_CREATED);
        assertThat(details(events.get(0))).containsEntry("version", 0L).containsEntry("name", "Apple").hasSize(2);
    }

    /** Bản ghi kiểm toán trỏ đúng đích (loại BRAND, mã thương hiệu) và đúng người thực hiện. */
    @Test
    @SuppressWarnings("unchecked")
    void auditRecordPointsAtTheBrandAndTheActor() {
        BrandView view = createBrand("Apple");

        Map<String, Object> audit = payload(stored().get(0));
        assertThat((Map<String, Object>) audit.get("target"))
                .containsEntry("type", CatalogAuditActions.TARGET_BRAND).containsEntry("id", view.id().toString());
        assertThat((Map<String, Object>) audit.get("actor"))
                .containsEntry("accountId", ADMIN.accountId().toString()).containsEntry("role", "ADMIN");
    }

    @Test
    void nonAdminIsRejectedBeforeAnythingElse() {
        assertCode(() -> create.handle(new CreateBrand.Command(USER, "Apple")), CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(repository.saveCount).isZero();
        verifyNoInteractions(outbox);
    }

    /** INV-CAT-20: tên trùng sau chuẩn hóa (khác dấu, hoa thường, khoảng trắng) bị từ chối sớm, không lưu, không ghi. */
    @Test
    void duplicateNameIsRejectedEarlyEvenWithDifferentAccentAndCase() {
        createBrand("Điện Quang");
        int records = stored().size();

        assertCode(() -> createBrand("DIEN   quang"), BrandErrorCode.BRAND_NAME_TAKEN);

        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(stored()).hasSize(records);
    }

    // ---- đổi tên ----

    @Test
    void renameUpdatesNameAndAuditsOldAndNewNameWithVersionOne() {
        BrandView created = createBrand("Apple");

        BrandView view = rename.handle(new RenameBrand.Command(ADMIN, created.id(), "Apple Inc"));

        assertThat(view.name()).isEqualTo("Apple Inc");
        assertThat(view.version()).isEqualTo(1L);
        DomainEvent audit = stored().get(1);
        assertThat(payload(audit).get("action")).isEqualTo(CatalogAuditActions.BRAND_UPDATED);
        assertThat(details(audit)).containsEntry("version", 1L).containsEntry("change", "RENAMED")
                .containsEntry("oldName", "Apple").containsEntry("newName", "Apple Inc").hasSize(4);
    }

    @Test
    void renameToAnotherBrandsNameIsRejectedButToOwnSpellingIsAllowed() {
        BrandView apple = createBrand("Apple");
        createBrand("Samsung");

        assertCode(() -> rename.handle(new RenameBrand.Command(ADMIN, apple.id(), "samsung")),
                BrandErrorCode.BRAND_NAME_TAKEN);

        BrandView view = rename.handle(new RenameBrand.Command(ADMIN, apple.id(), "APPLE"));
        assertThat(view.name()).isEqualTo("APPLE");
    }

    @Test
    void renameUnknownBrandIsNotFound() {
        assertCode(() -> rename.handle(new RenameBrand.Command(ADMIN, UUID.randomUUID(), "X")),
                BrandErrorCode.BRAND_NOT_FOUND);
    }

    @Test
    void renameChecksRoleBeforeLookingUpTheBrand() {
        assertCode(() -> rename.handle(new RenameBrand.Command(USER, UUID.randomUUID(), "X")),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
    }

    // ---- ngừng, bật lại ----

    @Test
    void deactivateAuditsOnlyVersionAndActivateAuditsUpdatedWithChange() {
        BrandView created = createBrand("Apple");

        BrandView off = new DeactivateBrand(updater).handle(new DeactivateBrand.Command(ADMIN, created.id()));
        BrandView on = new ActivateBrand(updater).handle(new ActivateBrand.Command(ADMIN, created.id()));

        assertThat(off.status()).isEqualTo(BrandStatus.INACTIVE);
        assertThat(off.version()).isEqualTo(1L);
        assertThat(on.status()).isEqualTo(BrandStatus.ACTIVE);
        assertThat(on.version()).isEqualTo(2L);
        List<DomainEvent> events = stored();
        assertThat(payload(events.get(1)).get("action")).isEqualTo(CatalogAuditActions.BRAND_DEACTIVATED);
        assertThat(details(events.get(1))).containsEntry("version", 1L).hasSize(1);
        assertThat(payload(events.get(2)).get("action")).isEqualTo(CatalogAuditActions.BRAND_UPDATED);
        assertThat(details(events.get(2))).containsEntry("version", 2L).containsEntry("change", "ACTIVATED").hasSize(2);
    }

    /** Q5: ngừng thứ đã ngừng là 409, không lưu thêm, không ghi kiểm toán. */
    @Test
    void deactivatingTwiceIsInvalidStateWithoutExtraRecord() {
        BrandView created = createBrand("Apple");
        new DeactivateBrand(updater).handle(new DeactivateBrand.Command(ADMIN, created.id()));
        int records = stored().size();
        int saves = repository.saveCount;

        assertCode(() -> new DeactivateBrand(updater).handle(new DeactivateBrand.Command(ADMIN, created.id())),
                CatalogErrorCode.CATALOG_INVALID_STATE);

        assertThat(stored()).hasSize(records);
        assertThat(repository.saveCount).isEqualTo(saves);
    }

    @Test
    void nonAdminCannotChangeAnExistingBrand() {
        BrandView created = createBrand("Apple");
        int records = stored().size();

        assertCode(() -> new DeactivateBrand(updater).handle(new DeactivateBrand.Command(USER, created.id())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(stored()).hasSize(records);
        assertThat(repository.stored(created.id()).version()).isZero();
    }

    @Test
    void unknownBrandIsNotFoundOnStatusChange() {
        assertCode(() -> new ActivateBrand(updater).handle(new ActivateBrand.Command(ADMIN, UUID.randomUUID())),
                BrandErrorCode.BRAND_NOT_FOUND);
    }

    @Test
    void auditFailureAbortsTheUseCaseSoCallerSeesTheError() {
        BrandView created = createBrand("Apple");
        doThrow(new IllegalStateException("audit down")).when(outbox).store(any());

        assertThatThrownBy(() -> rename.handle(new RenameBrand.Command(ADMIN, created.id(), "Apple Inc")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- đọc ----

    @Test
    void adminReadsRequireAdminWhilePublicListShowsOnlyActive() {
        BrandView apple = createBrand("Apple");
        createBrand("Samsung");
        new DeactivateBrand(updater).handle(new DeactivateBrand.Command(ADMIN, apple.id()));

        assertCode(() -> new GetBrand(repository).handle(new GetBrand.Query(USER, apple.id())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        assertCode(() -> new ListAdminBrands(repository).handle(new ListAdminBrands.Query(USER, null, null, 0, 20)),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(new GetBrand(repository).handle(new GetBrand.Query(ADMIN, apple.id())).status())
                .isEqualTo(BrandStatus.INACTIVE);
        assertThat(new ListAdminBrands(repository).handle(new ListAdminBrands.Query(ADMIN, null, null, 0, 20)).total())
                .isEqualTo(2);
        ListBrands.Result visible = new ListBrands(repository).handle(new ListBrands.Query(null, 0, 20));
        assertThat(visible.total()).isEqualTo(1);
        assertThat(visible.items()).extracting(ListBrands.Item::name).containsExactly("Samsung");
    }

    @Test
    void getUnknownBrandIsNotFound() {
        assertCode(() -> new GetBrand(repository).handle(new GetBrand.Query(ADMIN, UUID.randomUUID())),
                BrandErrorCode.BRAND_NOT_FOUND);
    }

    @Test
    void keywordSearchIgnoresCaseAccentsAndSpaces() {
        createBrand("Điện Quang");
        createBrand("Samsung");

        assertThat(new ListBrands(repository).handle(new ListBrands.Query("  DIEN ", 0, 20)).items())
                .extracting(ListBrands.Item::name).containsExactly("Điện Quang");
        // từ khóa có dấu và nhiều khoảng trắng vẫn khớp tên đã lưu (chuẩn hóa như tên)
        assertThat(new ListBrands(repository).handle(new ListBrands.Query("ĐIỆN   Qu", 0, 20)).items())
                .extracting(ListBrands.Item::name).containsExactly("Điện Quang");
        assertThat(new ListBrands(repository).handle(new ListBrands.Query("   ", 0, 20)).total()).isEqualTo(2);
        assertThat(new ListAdminBrands(repository).handle(new ListAdminBrands.Query(ADMIN, "sung", null, 0, 20)).total())
                .isEqualTo(1);
    }
}
