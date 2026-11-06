package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử sự kiện miền SellerProfileApproved.
 */
class SellerProfileApprovedTest {

    @Test
    @DisplayName("Khởi tạo thành công và kiểm tra các thuộc tính, payload và toString")
    void validEventHasCorrectPropertiesAndPayload() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant occurredOn = Instant.parse("2026-10-26T03:00:00Z");
        UUID accountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID profileUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        SellerProfileId profileId = SellerProfileId.of(profileUuid);
        long version = 4L;
        UUID reviewedBy = UUID.fromString("44444444-4444-4444-4444-444444444444");
        long viewedVersion = 3L;

        SellerProfileApproved event = new SellerProfileApproved(
                eventId, occurredOn, accountId, profileId, version, reviewedBy, viewedVersion
        );

        assertThat(event.getEventId()).isEqualTo(eventId.toString());
        assertThat(event.getAggregateType()).isEqualTo("SellerProfile");
        assertThat(event.getAggregateId()).isEqualTo(accountId.toString());
        assertThat(event.getOccurredOn()).isEqualTo(occurredOn);
        assertThat(event.getRoutingKey()).isEqualTo("seller.profile.approved");

        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.profileId()).isEqualTo(profileId);
        assertThat(event.version()).isEqualTo(version);
        assertThat(event.reviewedBy()).isEqualTo(reviewedBy);
        assertThat(event.viewedVersion()).isEqualTo(viewedVersion);

        // Kiểm tra payload
        Object payloadObj = event.getPayload();
        assertThat(payloadObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadObj;

        // Đúng 4 khóa theo thứ tự accountId, profileId, version, occurredAt
        assertThat(payload.keySet()).containsExactly("accountId", "profileId", "version", "occurredAt");
        assertThat(payload.get("accountId")).isEqualTo(accountId.toString());
        assertThat(payload.get("profileId")).isEqualTo(profileUuid.toString());
        assertThat(payload.get("version")).isEqualTo(version);
        assertThat(payload.get("occurredAt")).isEqualTo(occurredOn.toString());

        // Payload KHÔNG chứa khóa reviewedBy, viewedVersion, shopName, legalName, identityNumber, taxCode, businessAddress, status
        assertThat(payload).doesNotContainKeys(
                "reviewedBy", "viewedVersion", "shopName", "legalName", "identityNumber", "taxCode", "businessAddress", "status"
        );

        // toString() chứa tên lớp, eventId, mã hồ sơ (UUID); KHÔNG chứa accountId, reviewedBy
        String eventStr = event.toString();
        assertThat(eventStr).contains(SellerProfileApproved.class.getSimpleName());
        assertThat(eventStr).contains(eventId.toString());
        assertThat(eventStr).contains(profileUuid.toString());
        assertThat(eventStr).doesNotContain(accountId.toString());
        assertThat(eventStr).doesNotContain(reviewedBy.toString());
    }

    @Test
    @DisplayName("Hàm tạo ném NullPointerException khi tham số tham chiếu null")
    void constructorThrowsNullPointerExceptionOnNullArguments() {
        UUID eventId = UUID.randomUUID();
        Instant occurredOn = Instant.now();
        UUID accountId = UUID.randomUUID();
        SellerProfileId profileId = SellerProfileId.of(UUID.randomUUID());
        long version = 1L;
        UUID reviewedBy = UUID.randomUUID();
        long viewedVersion = 0L;

        assertThatThrownBy(() -> new SellerProfileApproved(null, occurredOn, accountId, profileId, version, reviewedBy, viewedVersion))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileApproved(eventId, null, accountId, profileId, version, reviewedBy, viewedVersion))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileApproved(eventId, occurredOn, null, profileId, version, reviewedBy, viewedVersion))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileApproved(eventId, occurredOn, accountId, null, version, reviewedBy, viewedVersion))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileApproved(eventId, occurredOn, accountId, profileId, version, null, viewedVersion))
                .isInstanceOf(NullPointerException.class);
    }
}
