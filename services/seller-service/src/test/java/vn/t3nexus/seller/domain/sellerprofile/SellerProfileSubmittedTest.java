package vn.t3nexus.seller.domain.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử sự kiện miền SellerProfileSubmitted.
 */
class SellerProfileSubmittedTest {

    @Test
    @DisplayName("Khởi tạo thành công và kiểm tra các thuộc tính, payload và toString")
    void validEventHasCorrectPropertiesAndPayload() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant occurredOn = Instant.parse("2026-10-26T03:00:00Z");
        UUID accountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID profileUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        SellerProfileId profileId = SellerProfileId.of(profileUuid);
        Instant submittedAt = Instant.parse("2026-10-26T03:05:00Z");

        SellerProfileSubmitted event = new SellerProfileSubmitted(eventId, occurredOn, accountId, profileId, submittedAt);

        assertThat(event.getEventId()).isEqualTo(eventId.toString());
        assertThat(event.getAggregateType()).isEqualTo("SellerProfile");
        assertThat(event.getAggregateId()).isEqualTo(accountId.toString());
        assertThat(event.getOccurredOn()).isEqualTo(occurredOn);
        assertThat(event.getRoutingKey()).isEqualTo("seller.profile.submitted");

        assertThat(event.accountId()).isEqualTo(accountId);
        assertThat(event.profileId()).isEqualTo(profileId);
        assertThat(event.submittedAt()).isEqualTo(submittedAt);

        // Kiểm tra payload
        Object payloadObj = event.getPayload();
        assertThat(payloadObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadObj;

        // Đúng 3 khóa theo thứ tự accountId, profileId, submittedAt
        assertThat(payload.keySet()).containsExactly("accountId", "profileId", "submittedAt");
        assertThat(payload.get("accountId")).isEqualTo(accountId.toString());
        assertThat(payload.get("profileId")).isEqualTo(profileUuid.toString());
        assertThat(payload.get("submittedAt")).isEqualTo(submittedAt.toString());

        // Payload không chứa các khóa nhạy cảm hoặc trạng thái
        assertThat(payload).doesNotContainKeys(
                "sellerType", "shopName", "legalName", "identityNumber", "taxCode", "businessAddress", "status"
        );

        // toString() chỉ tên lớp, mã sự kiện, mã hồ sơ; không chứa submittedAt và accountId
        String eventStr = event.toString();
        assertThat(eventStr).contains(SellerProfileSubmitted.class.getSimpleName());
        assertThat(eventStr).contains(eventId.toString());
        assertThat(eventStr).contains(profileUuid.toString());
        assertThat(eventStr).doesNotContain(submittedAt.toString());
        assertThat(eventStr).doesNotContain(accountId.toString());
    }

    @Test
    @DisplayName("Hàm tạo ném NullPointerException khi tham số null")
    void constructorThrowsNullPointerExceptionOnNullArguments() {
        UUID eventId = UUID.randomUUID();
        Instant occurredOn = Instant.now();
        UUID accountId = UUID.randomUUID();
        SellerProfileId profileId = SellerProfileId.of(UUID.randomUUID());
        Instant submittedAt = Instant.now();

        assertThatThrownBy(() -> new SellerProfileSubmitted(null, occurredOn, accountId, profileId, submittedAt))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileSubmitted(eventId, null, accountId, profileId, submittedAt))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileSubmitted(eventId, occurredOn, null, profileId, submittedAt))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileSubmitted(eventId, occurredOn, accountId, null, submittedAt))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new SellerProfileSubmitted(eventId, occurredOn, accountId, profileId, null))
                .isInstanceOf(NullPointerException.class);
    }
}
