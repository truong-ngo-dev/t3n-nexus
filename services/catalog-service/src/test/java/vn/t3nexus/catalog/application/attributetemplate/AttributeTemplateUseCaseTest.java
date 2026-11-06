package vn.t3nexus.catalog.application.attributetemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.catalog.application.CatalogAuditActions;
import vn.t3nexus.catalog.application.attributetemplate.event.AttributeTemplateChangedAuditHandler;
import vn.t3nexus.catalog.application.attributetemplate.event.AttributeTemplateChangedOutboxHandler;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeTemplateErrorCode;
import vn.t3nexus.catalog.domain.attributetemplate.InputType;
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
 * KT-03 cho mẫu thuộc tính: use case với kho giả. Thứ tự kiểm vai, kiểm sớm, lưu, event; event mang
 * {@code version() + 1}; bản ghi kiểm toán và hộp thư đúng hành động, đúng dữ kiện. Dùng danh mục hành động THẬT của
 * {@link AuditConfig} nên khóa dữ kiện chưa khai báo làm test đỏ.
 */
class AttributeTemplateUseCaseTest {

    private static final CatalogActor ADMIN = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000aa"), "ADMIN");
    private static final CatalogActor USER = new CatalogActor(UUID.fromString("00000000-0000-7000-8000-0000000000bb"), "USER");

    private FakeAttributeTemplateRepository repository;
    private OutboxEventStore outbox;
    private EventDispatcher dispatcher;
    private IdGenerator ids;
    private Clock clock;
    private TemplateUpdater updater;
    private CreateAttributeTemplate create;

    @BeforeEach
    void setUp() {
        repository = new FakeAttributeTemplateRepository();
        outbox = mock(OutboxEventStore.class);
        ids = new UuidV7IdGenerator();
        clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        AuditRecorder recorder = new AuditRecorder(outbox, new AuditConfig().auditActionCatalog(), "catalog-service", clock, ids);
        dispatcher = new EventDispatcher(List.of(
                new AttributeTemplateChangedAuditHandler(recorder),
                new AttributeTemplateChangedOutboxHandler(outbox)));
        updater = new TemplateUpdater(repository, ids, clock, dispatcher);
        create = new CreateAttributeTemplate(repository, ids, clock, dispatcher);
    }

    private TemplateView createSelect(String name) {
        return create.handle(new CreateAttributeTemplate.Command(ADMIN, name, name, null, InputType.SINGLE_SELECT, null));
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
    void createSavesTemplateAndWritesAuditThenOutboxWithVersionZero() {
        TemplateView view = createSelect("color");

        assertThat(view.version()).isZero();
        assertThat(repository.stored(view.id())).isNotNull();
        List<DomainEvent> events = stored();
        assertThat(events).hasSize(2);
        // kiểm toán chạy trước hộp thư
        assertThat(payload(events.get(0)).get("action")).isEqualTo(CatalogAuditActions.ATTRIBUTE_CREATED);
        assertThat(details(events.get(0))).containsEntry("version", 0L).containsEntry("name", "color")
                .containsEntry("inputType", "SINGLE_SELECT").hasSize(3);
        assertThat(events.get(1).getRoutingKey()).isEqualTo("catalog.attribute-template.changed");
        assertThat(payload(events.get(1))).containsEntry("templateId", view.id().toString())
                .containsEntry("version", 0L).containsEntry("change", "CREATED");
        assertThat(events.get(1).getAggregateId()).isEqualTo(view.id().toString());
    }

    @Test
    void nonAdminIsRejectedBeforeAnythingElse() {
        assertCode(() -> create.handle(new CreateAttributeTemplate.Command(USER, "color", "Màu", null, InputType.TEXT, null)),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(repository.saveCount).isZero();
        verifyNoInteractions(outbox);
    }

    @Test
    void nonAdminCannotChangeAnExistingTemplate() {
        TemplateView created = createSelect("color");
        int records = stored().size();

        assertCode(() -> new DeactivateAttributeTemplate(updater)
                        .handle(new DeactivateAttributeTemplate.Command(USER, created.id())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(stored()).hasSize(records);
        assertThat(repository.stored(created.id()).version()).isZero();
    }

    @Test
    void duplicateNameIsRejectedEarly() {
        createSelect("color");

        assertCode(() -> createSelect("color"), AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NAME_TAKEN);
        assertThat(repository.saveCount).isEqualTo(1);
        assertThat(stored()).hasSize(2); // chỉ hai bản ghi của lần tạo đầu
    }

    // ---- sửa ----

    @Test
    void deactivateCarriesStoredVersionPlusOneAndAuditsDeactivation() {
        TemplateView created = createSelect("color");

        TemplateView view = new DeactivateAttributeTemplate(updater)
                .handle(new DeactivateAttributeTemplate.Command(ADMIN, created.id()));

        assertThat(view.version()).isEqualTo(1L);
        assertThat(repository.stored(created.id()).version()).isEqualTo(1L);
        List<DomainEvent> events = stored();
        assertThat(payload(events.get(2)).get("action")).isEqualTo(CatalogAuditActions.ATTRIBUTE_DEACTIVATED);
        assertThat(details(events.get(2))).containsOnlyKeys("version").containsEntry("version", 1L);
        assertThat(payload(events.get(3))).containsEntry("version", 1L).containsEntry("change", "DEACTIVATED");
    }

    @Test
    void deactivatingTwiceIsInvalidStateWithNoSecondRecord() {
        TemplateView created = createSelect("color");
        DeactivateAttributeTemplate deactivate = new DeactivateAttributeTemplate(updater);
        deactivate.handle(new DeactivateAttributeTemplate.Command(ADMIN, created.id()));
        int before = stored().size();

        assertCode(() -> deactivate.handle(new DeactivateAttributeTemplate.Command(ADMIN, created.id())),
                CatalogErrorCode.CATALOG_INVALID_STATE);

        assertThat(stored()).hasSize(before);
        assertThat(repository.stored(created.id()).version()).isEqualTo(1L);
    }

    @Test
    void unknownTemplateIsNotFound() {
        assertCode(() -> new ActivateAttributeTemplate(updater)
                        .handle(new ActivateAttributeTemplate.Command(ADMIN, UUID.randomUUID())),
                AttributeTemplateErrorCode.ATTRIBUTE_TEMPLATE_NOT_FOUND);
    }

    @Test
    void addOptionAuditsAsUpdatedWithOptionIdAndValue() {
        TemplateView created = createSelect("color");

        TemplateView view = new AddAttributeOption(updater, ids)
                .handle(new AddAttributeOption.Command(ADMIN, created.id(), "red", "Đỏ"));

        assertThat(view.version()).isEqualTo(1L);
        assertThat(view.options()).hasSize(1);
        List<DomainEvent> events = stored();
        Map<String, Object> d = details(events.get(2));
        assertThat(payload(events.get(2)).get("action")).isEqualTo(CatalogAuditActions.ATTRIBUTE_UPDATED);
        assertThat(d).containsEntry("change", "OPTION_ADDED").containsEntry("value", "red")
                .containsEntry("optionId", view.options().get(0).id().toString()).containsEntry("version", 1L);
    }

    @Test
    void duplicateOptionIsRejectedAndLeavesStoredTemplateUntouched() {
        TemplateView created = createSelect("color");
        AddAttributeOption add = new AddAttributeOption(updater, ids);
        add.handle(new AddAttributeOption.Command(ADMIN, created.id(), "red", "Đỏ"));
        int records = stored().size();

        assertCode(() -> add.handle(new AddAttributeOption.Command(ADMIN, created.id(), "red", "Đỏ 2")),
                AttributeTemplateErrorCode.ATTRIBUTE_OPTION_VALUE_TAKEN);

        assertThat(stored()).hasSize(records);
        assertThat(repository.stored(created.id()).getOptions()).hasSize(1);
    }

    @Test
    void auditFailureAbortsTheUseCaseSoCallerSeesTheError() {
        TemplateView created = createSelect("color");
        doThrow(new IllegalStateException("audit down")).when(outbox).store(any());

        assertThatThrownBy(() -> new UpdateAttributeTemplate(updater)
                .handle(new UpdateAttributeTemplate.Command(ADMIN, created.id(), "Màu mới", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateReturnsNewDisplayNameAndVersion() {
        TemplateView created = createSelect("color");

        TemplateView view = new UpdateAttributeTemplate(updater)
                .handle(new UpdateAttributeTemplate.Command(ADMIN, created.id(), "Màu mới", "gợi ý"));

        assertThat(view.displayName()).isEqualTo("Màu mới");
        assertThat(view.hint()).isEqualTo("gợi ý");
        assertThat(view.version()).isEqualTo(1L);
    }

    // ---- đọc ----

    @Test
    void readsRequireAdmin() {
        TemplateView created = createSelect("color");

        assertCode(() -> new GetAttributeTemplate(repository).handle(new GetAttributeTemplate.Query(USER, created.id())),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);
        assertCode(() -> new ListAttributeTemplates(repository)
                        .handle(new ListAttributeTemplates.Query(USER, null, null, null, 0, 20)),
                CatalogErrorCode.CATALOG_ROLE_NOT_ALLOWED);

        assertThat(new GetAttributeTemplate(repository).handle(new GetAttributeTemplate.Query(ADMIN, created.id()))
                .version()).isZero();
        assertThat(new ListAttributeTemplates(repository)
                .handle(new ListAttributeTemplates.Query(ADMIN, null, null, null, 0, 20)).total()).isEqualTo(1);
    }
}
