package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử sự kiện miền SellerProfileRejected.
 */
class SellerProfileRejectedTest {

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
        String reason = "Giấy tờ mờ";

        SellerProfileRejected event = new SellerProfileRejected(
                eventId, occurredOn, accountId, profileId, version, reviewedBy, viewedVersion, reason
        );

        assertThat(event.getEventId()).isEqualTo(eventId.toString());
        assertThat(event.getAggregateType()).isEqualTo("SellerProfile");
        assertThat(event.getAggregateId()).isEqualTo(accountId.toString());
        assertThat(event.getOccurredOn()).isEqualTo(occurredOn);
        assertThat(event.getRoutingKey()).isEqualTo("seller.profile.rejected");

        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.profileId()).isEqualTo(profileId);
        assertThat(event.version()).isEqualTo(version);
        assertThat(event.reviewedBy()).isEqualTo(reviewedBy);
        assertThat(event.viewedVersion()).isEqualTo(viewedVersion);
        assertThat(event.reason()).isEqualTo(reason);

        // Kiểm tra payload
        Object payloadObj = event.getPayload();
        assertThat(payloadObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadObj;

        // Đúng 3 khóa theo thứ tự accountId, version, occurredAt (KHÔNG có profileId, KHÔNG có reason, KHÔNG có reviewedBy, viewedVersion)
        assertThat(payload.keySet()).containsExactly("accountId", "version", "occurredAt");
        assertThat(payload.get("accountId")).isEqualTo(accountId.toString());
        assertThat(payload.get("version")).isEqualTo(version);
        assertThat(payload.get("occurredAt")).isEqualTo(occurredOn.toString());

        // Kiểm tra payload không chứa reason ngay cả khi lý do là "Giấy tờ mờ", và không chứa profileId, reviewedBy, viewedVersion
        assertThat(payload).doesNotContainKeys("profileId", "reason", "reviewedBy", "viewedVersion");
        assertThat(payload.values()).doesNotContain("Giấy tờ mờ");

        // toString() chứa tên lớp, eventId, mã hồ sơ; KHÔNG chứa accountId, reviewedBy, lý do
        String eventStr = event.toString();
        assertThat(eventStr).contains(SellerProfileRejected.class.getSimpleName());
        assertThat(eventStr).contains(eventId.toString());
        assertThat(eventStr).contains(profileUuid.toString());
        assertThat(eventStr).doesNotContain(accountId.toString());
        assertThat(eventStr).doesNotContain(reviewedBy.toString());
        assertThat(eventStr).doesNotContain("Giấy tờ mờ");
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
        String reason = "Lý do từ chối";

        assertThatThrownBy(() -> new SellerProfileRejected(null, occurredOn, accountId, profileId, version, reviewedBy, viewedVersion, reason))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileRejected(eventId, null, accountId, profileId, version, reviewedBy, viewedVersion, reason))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileRejected(eventId, occurredOn, null, profileId, version, reviewedBy, viewedVersion, reason))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileRejected(eventId, occurredOn, accountId, null, version, reviewedBy, viewedVersion, reason))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileRejected(eventId, occurredOn, accountId, profileId, version, null, viewedVersion, reason))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileRejected(eventId, occurredOn, accountId, profileId, version, reviewedBy, viewedVersion, null))
                .isInstanceOf(NullPointerException.class);
    }
}
