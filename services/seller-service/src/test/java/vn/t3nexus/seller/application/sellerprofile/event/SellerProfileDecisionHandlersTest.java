package vn.t3nexus.seller.application.sellerprofile.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileApproved;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRejected;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Kiểm thử đơn vị cho 4 event handler quyết định duyệt/từ chối hồ sơ người bán.
 */
class SellerProfileDecisionHandlersTest {

    @Test
    @DisplayName("SellerProfileApprovedHandler: getEventType đúng lớp và lưu đúng event vào outbox")
    void approvedHandlerStoresEventInOutbox() {
        OutboxEventStore outbox = mock(OutboxEventStore.class);
        SellerProfileApprovedHandler handler = new SellerProfileApprovedHandler(outbox);

        assertThat(handler.getEventType()).isEqualTo(SellerProfileApproved.class);

        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant occurredOn = Instant.parse("2026-10-26T03:00:00Z");
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfileId profileId = SellerProfileId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        SellerProfileApproved event = new SellerProfileApproved(
                eventId,
                occurredOn,
                accountId,
                profileId,
                4L,
                adminId,
                3L
        );

        handler.handle(event);

        verify(outbox).store(event);
    }

    @Test
    @DisplayName("SellerProfileRejectedHandler: getEventType đúng lớp và lưu đúng event vào outbox")
    void rejectedHandlerStoresEventInOutbox() {
        OutboxEventStore outbox = mock(OutboxEventStore.class);
        SellerProfileRejectedHandler handler = new SellerProfileRejectedHandler(outbox);

        assertThat(handler.getEventType()).isEqualTo(SellerProfileRejected.class);

        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant occurredOn = Instant.parse("2026-10-26T03:00:00Z");
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SellerProfileId profileId = SellerProfileId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        SellerProfileRejected event = new SellerProfileRejected(
                eventId,
                occurredOn,
                accountId,
                profileId,
                4L,
                adminId,
                3L,
                "Giấy tờ mờ"
        );

        handler.handle(event);

        verify(outbox).store(event);
    }

    @Test
    @DisplayName("SellerProfileApprovedAuditHandler: getEventType đúng lớp và gọi recorder.record đúng đối số")
    void approvedAuditHandlerRecordsAudit() {
        AuditRecorder recorder = mock(AuditRecorder.class);
        SellerProfileApprovedAuditHandler handler = new SellerProfileApprovedAuditHandler(recorder);

        assertThat(handler.getEventType()).isEqualTo(SellerProfileApproved.class);

        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant occurredOn = Instant.parse("2026-10-26T03:00:00Z");
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        SellerProfileId profileId = SellerProfileId.of(profileUuid);
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        SellerProfileApproved event = new SellerProfileApproved(
                eventId,
                occurredOn,
                accountId,
                profileId,
                4L,
                adminId,
                3L
        );

        handler.handle(event);

        verify(recorder).record(
                "SELLER_PROFILE_APPROVED",
                new AuditActor(adminId, "ADMIN"),
                new AuditTarget("SELLER_PROFILE", profileUuid.toString()),
                Map.of("viewedVersion", 3L, "sellerAccountId", accountId.toString())
        );
    }

    @Test
    @DisplayName("SellerProfileRejectedAuditHandler: getEventType đúng lớp và gọi recorder.record đúng đối số gồm rejectionReason")
    void rejectedAuditHandlerRecordsAudit() {
        AuditRecorder recorder = mock(AuditRecorder.class);
        SellerProfileRejectedAuditHandler handler = new SellerProfileRejectedAuditHandler(recorder);

        assertThat(handler.getEventType()).isEqualTo(SellerProfileRejected.class);

        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant occurredOn = Instant.parse("2026-10-26T03:00:00Z");
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        SellerProfileId profileId = SellerProfileId.of(profileUuid);
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        SellerProfileRejected event = new SellerProfileRejected(
                eventId,
                occurredOn,
                accountId,
                profileId,
                4L,
                adminId,
                3L,
                "Giấy tờ mờ"
        );

        handler.handle(event);

        verify(recorder).record(
                "SELLER_PROFILE_REJECTED",
                new AuditActor(adminId, "ADMIN"),
                new AuditTarget("SELLER_PROFILE", profileUuid.toString()),
                Map.of(
                        "viewedVersion", 3L,
                        "sellerAccountId", accountId.toString(),
                        "rejectionReason", "Giấy tờ mờ"
                )
        );
    }

    @Test
    @DisplayName("getOrder của hai handler kiểm toán là 200 và lớn hơn handler hộp thư (100)")
    void orderOfAuditHandlersIsHigherThanOutboxHandlers() {
        OutboxEventStore outbox = mock(OutboxEventStore.class);
        AuditRecorder recorder = mock(AuditRecorder.class);

        SellerProfileApprovedHandler approvedOutbox = new SellerProfileApprovedHandler(outbox);
        SellerProfileRejectedHandler rejectedOutbox = new SellerProfileRejectedHandler(outbox);

        SellerProfileApprovedAuditHandler approvedAudit = new SellerProfileApprovedAuditHandler(recorder);
        SellerProfileRejectedAuditHandler rejectedAudit = new SellerProfileRejectedAuditHandler(recorder);

        assertThat(approvedOutbox.getOrder()).isEqualTo(100);
        assertThat(rejectedOutbox.getOrder()).isEqualTo(100);

        assertThat(approvedAudit.getOrder()).isEqualTo(200);
        assertThat(rejectedAudit.getOrder()).isEqualTo(200);

        assertThat(approvedAudit.getOrder()).isGreaterThan(approvedOutbox.getOrder());
        assertThat(rejectedAudit.getOrder()).isGreaterThan(rejectedOutbox.getOrder());
    }
}
