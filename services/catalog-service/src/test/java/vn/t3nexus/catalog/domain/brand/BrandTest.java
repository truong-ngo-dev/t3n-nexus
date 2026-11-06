package vn.t3nexus.catalog.domain.brand;

import org.junit.jupiter.api.Test;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.CatalogErrorCode;
import vn.t3nexus.catalog.domain.CatalogFieldException;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.model.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** KT-01 cho AGG-CAT-03: thương hiệu (INV-CAT-20, 21). */
class BrandTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-7000-8000-0000000000aa");

    private static ChangeContext ctx() {
        return new ChangeContext(new CatalogActor(ADMIN, "ADMIN"), UUID.randomUUID(), NOW);
    }

    private static Brand brand() {
        Brand b = Brand.create(BrandId.of(UUID.randomUUID()), "Apple", ctx());
        b.clearDomainEvents();
        return b;
    }

    private static BrandChanged lastEvent(Brand b) {
        List<DomainEvent> events = List.copyOf(b.getDomainEvents());
        return (BrandChanged) events.get(events.size() - 1);
    }

    // ---- tạo ----

    @Test
    void createsActiveBrandKeepingDisplayNameAndDerivingKey() {
        Brand b = Brand.create(BrandId.of(UUID.randomUUID()), "  Điện   Quang ", ctx());

        assertThat(b.getName()).isEqualTo("Điện Quang");
        assertThat(b.getNameKey()).isEqualTo("dien quang");
        assertThat(b.getStatus()).isEqualTo(BrandStatus.ACTIVE);
        assertThat(b.isActive()).isTrue();
        assertThat(b.version()).isNull();
    }

    @Test
    void creationEmitsEventWithVersionZeroAndName() {
        Brand b = Brand.create(BrandId.of(UUID.randomUUID()), "Apple", ctx());

        BrandChanged event = lastEvent(b);
        assertThat(event.change()).isEqualTo(BrandChanged.Change.CREATED);
        assertThat(event.version()).isZero();
        assertThat(event.actorId()).isEqualTo(ADMIN);
        assertThat(event.name()).isEqualTo("Apple");
        assertThat(event.oldName()).isNull();
    }

    @Test
    void rejectsBlankOrTooLongName() {
        BrandId id = BrandId.of(UUID.randomUUID());
        for (String bad : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> Brand.create(id, bad, ctx()))
                    .isInstanceOf(CatalogFieldException.class)
                    .extracting(e -> ((CatalogFieldException) e).field()).isEqualTo("name");
        }
        assertThatThrownBy(() -> Brand.create(id, "a".repeat(101), ctx())).isInstanceOf(CatalogFieldException.class);
        assertThat(Brand.create(id, "a".repeat(100), ctx()).getName()).hasSize(100);
    }

    /** INV-CAT-20: khóa tên giống nhau với mọi cách viết cùng một tên. */
    @Test
    void nameKeyIgnoresCaseAccentsAndSpaces() {
        assertThat(Brand.nameKeyOf(" ĐIỆN   quang ")).isEqualTo(Brand.nameKeyOf("dien quang"));
        assertThat(Brand.nameKeyOf("Apple")).isNotEqualTo(Brand.nameKeyOf("Aple"));
    }

    // ---- đổi tên ----

    @Test
    void renameChangesNameAndKeyAndEmitsOldAndNewName() {
        Brand b = brand();

        b.rename("  Táo   Khuyết ", ctx());

        assertThat(b.getName()).isEqualTo("Táo Khuyết");
        assertThat(b.getNameKey()).isEqualTo("tao khuyet");
        BrandChanged event = lastEvent(b);
        assertThat(event.change()).isEqualTo(BrandChanged.Change.RENAMED);
        assertThat(event.oldName()).isEqualTo("Apple");
        assertThat(event.name()).isEqualTo("Táo Khuyết");
    }

    @Test
    void renameRejectsBlankAndKeepsOldName() {
        Brand b = brand();

        assertThatThrownBy(() -> b.rename(" ", ctx())).isInstanceOf(CatalogFieldException.class);

        assertThat(b.getName()).isEqualTo("Apple");
        assertThat(b.getNameKey()).isEqualTo("apple");
        assertThat(b.getDomainEvents()).isEmpty();
    }

    @Test
    void renameAllowedWhenInactive() {
        Brand b = brand();
        b.deactivate(ctx());

        b.rename("Apple Inc", ctx());

        assertThat(b.getName()).isEqualTo("Apple Inc");
        assertThat(b.getStatus()).isEqualTo(BrandStatus.INACTIVE);
    }

    // ---- ngừng, bật lại ----

    @Test
    void deactivateThenActivate() {
        Brand b = brand();

        b.deactivate(ctx());
        assertThat(b.isActive()).isFalse();
        assertThat(lastEvent(b).change()).isEqualTo(BrandChanged.Change.DEACTIVATED);

        b.activate(ctx());
        assertThat(b.isActive()).isTrue();
        assertThat(lastEvent(b).change()).isEqualTo(BrandChanged.Change.ACTIVATED);
        assertThat(b.getDomainEvents()).hasSize(2);
    }

    /** Q5: ngừng hay bật lại thứ đã ở trạng thái đó là 409, không đổi gì, không phát event. */
    @Test
    void settingTheSameStateIsRejectedWithoutEvent() {
        Brand b = brand();

        assertThatThrownBy(() -> b.activate(ctx())).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(CatalogErrorCode.CATALOG_INVALID_STATE);
        b.deactivate(ctx());
        b.clearDomainEvents();
        assertThatThrownBy(() -> b.deactivate(ctx())).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode()).isEqualTo(CatalogErrorCode.CATALOG_INVALID_STATE);

        assertThat(b.getDomainEvents()).isEmpty();
        assertThat(b.getStatus()).isEqualTo(BrandStatus.INACTIVE);
    }

    @Test
    void neverDeletedOnlyDeactivated() {
        BrandRepository repo = new BrandRepository() {
            public java.util.Optional<Brand> findById(BrandId id) { return java.util.Optional.empty(); }
            public void save(Brand brand) { }
            public boolean existsByNameKey(String nameKey) { return false; }
            public boolean existsByNameKeyExcludingId(String nameKey, BrandId excludingId) { return false; }
            public List<Brand> search(String keywordKey, BrandStatus status, int page, int size) { return List.of(); }
            public long count(String keywordKey, BrandStatus status) { return 0; }
        };

        assertThatThrownBy(() -> repo.delete(BrandId.of(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- sự kiện và phiên bản ----

    @Test
    void eventVersionIsStoredVersionPlusOne() {
        Brand stored = Brand.reconstitute(BrandId.of(UUID.randomUUID()), "Apple", "apple", BrandStatus.ACTIVE, 4L, NOW, NOW);

        stored.deactivate(ctx());

        assertThat(lastEvent(stored).version()).isEqualTo(5L);
        assertThat(stored.nextVersion()).isEqualTo(5L);
        assertThat(stored.version()).isEqualTo(4L);
    }

    @Test
    void newBrandNextVersionIsZero() {
        assertThat(Brand.create(BrandId.of(UUID.randomUUID()), "Apple", ctx()).nextVersion()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void eventPayloadHasOnlyIdVersionChangeAndTime() {
        Brand b = brand();
        b.rename("Apple Inc", ctx());

        assertThat(lastEvent(b).getRoutingKey()).isEqualTo("catalog.brand.changed");
        assertThat(lastEvent(b).getAggregateId()).isEqualTo(b.getId().getValue().toString());
        assertThat(((java.util.Map<String, Object>) lastEvent(b).getPayload()).keySet())
                .containsExactly("brandId", "version", "change", "occurredAt");
    }
}
