package vn.t3nexus.seller.application.sellerprofile.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileSubmitted;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Kiểm thử đơn vị cho {@link SellerProfileSubmittedHandler}: nộp và nộp lại cùng phát một loại sự kiện nên dùng chung một handler.
 */
class SellerProfileSubmittedHandlerTest {

    @Test
    @DisplayName("getEventType đúng lớp và lưu đúng sự kiện vào hộp thư, không làm gì thêm")
    void storesTheEventInTheOutboxAndNothingElse() {
        OutboxEventStore outbox = mock(OutboxEventStore.class);
        SellerProfileSubmittedHandler handler = new SellerProfileSubmittedHandler(outbox);

        assertThat(handler.getEventType()).isEqualTo(SellerProfileSubmitted.class);

        SellerProfileSubmitted event = new SellerProfileSubmitted(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Instant.parse("2026-10-26T03:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                SellerProfileId.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
                Instant.parse("2026-10-26T03:00:00Z")
        );

        handler.handle(event);

        verify(outbox).store(event);
        verifyNoMoreInteractions(outbox);
    }

    @Test
    @DisplayName("Không nhận hộp thư rỗng")
    void rejectsNullOutbox() {
        assertThatThrownBy(() -> new SellerProfileSubmittedHandler(null)).isInstanceOf(NullPointerException.class);
    }
}
