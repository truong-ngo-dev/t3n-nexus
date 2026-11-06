package vn.t3nexus.lib.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.outbox.OutboxEventStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditRecorderTest {

    private OutboxEventStore store;
    private AuditActionCatalog catalog;
    private Clock fixedClock;
    private IdGenerator fakeIdGenerator;
    private AuditRecorder recorder;

    private final UUID fixedUuid = UUID.fromString("018f3a55-0000-7000-8000-000000000001");
    private final Instant fixedInstant = Instant.parse("2026-10-24T10:00:00Z");

    @BeforeEach
    void setUp() {
        store = mock(OutboxEventStore.class);
        catalog = AuditActionCatalog.of(
                new AuditActionDeclaration("SELLER_PROFILE_REJECTED", Set.of("reason", "profileVersion"))
        );
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);
        fakeIdGenerator = () -> fixedUuid;

        recorder = new AuditRecorder(store, catalog, "seller-service", fixedClock, fakeIdGenerator);
    }

    @Test
    void recordValidActionStoresEventOnceWithCorrectFields() {
        UUID actorId = UUID.randomUUID();
        AuditActor actor = new AuditActor(actorId, "ADMIN");
        AuditTarget target = new AuditTarget("SELLER_PROFILE", "acc-1");
        Map<String, Object> details = Map.of("reason", "expired license");

        recorder.record("SELLER_PROFILE_REJECTED", actor, target, details);

        ArgumentCaptor<AuditActionRecorded> captor = ArgumentCaptor.forClass(AuditActionRecorded.class);
        verify(store).store(captor.capture());

        AuditActionRecorded event = captor.getValue();
        assertThat(event.getEventId()).isEqualTo(fixedUuid.toString());
        assertThat(event.getOccurredOn()).isEqualTo(fixedInstant);
        assertThat(event.getAggregateType()).isEqualTo("SELLER_PROFILE");
        assertThat(event.getAggregateId()).isEqualTo("acc-1");
        assertThat(event.getRoutingKey()).isEqualTo(AuditContract.TOPIC);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.getPayload();
        assertThat(payload.get("sourceService")).isEqualTo("seller-service");
        assertThat(payload.get("action")).isEqualTo("SELLER_PROFILE_REJECTED");
        assertThat(payload.get("details")).isEqualTo(details);
    }

    @Test
    void undeclaredActionThrowsAndStoreIsNotCalled() {
        AuditActor actor = new AuditActor(UUID.randomUUID(), "ADMIN");
        AuditTarget target = new AuditTarget("SELLER_PROFILE", "acc-1");

        assertThatThrownBy(() -> recorder.record("UNKNOWN_ACTION", actor, target, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Hành động chưa khai báo: UNKNOWN_ACTION");

        verifyNoInteractions(store);
    }

    @Test
    void undeclaredDetailKeyThrowsAndStoreIsNotCalled() {
        AuditActor actor = new AuditActor(UUID.randomUUID(), "ADMIN");
        AuditTarget target = new AuditTarget("SELLER_PROFILE", "acc-1");

        assertThatThrownBy(() -> recorder.record("SELLER_PROFILE_REJECTED", actor, target, Map.of("undeclaredKey", "val")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Khóa dữ kiện chưa khai báo cho hành động: undeclaredKey");

        verifyNoInteractions(store);
    }

    @Test
    void forbiddenKeyInDetailsThrowsAndStoreIsNotCalled() {
        AuditActor actor = new AuditActor(UUID.randomUUID(), "ADMIN");
        AuditTarget target = new AuditTarget("SELLER_PROFILE", "acc-1");

        assertThatThrownBy(() -> recorder.record("SELLER_PROFILE_REJECTED", actor, target, Map.of("email", "seller@example.com")))
                .isInstanceOf(InvalidAuditDetailsException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("FORBIDDEN_NAME");

        verifyNoInteractions(store);
    }
}
