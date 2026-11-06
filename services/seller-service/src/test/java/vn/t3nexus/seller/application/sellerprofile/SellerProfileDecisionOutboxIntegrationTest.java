package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.audit.AuditActor;
import vn.t3nexus.lib.audit.AuditRecord;
import vn.t3nexus.lib.audit.AuditRecordReader;
import vn.t3nexus.lib.audit.AuditTarget;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.events.EventEnvelope;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.submitCommand;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử tích hợp hộp thư outbox khi duyệt hoặc từ chối hồ sơ người bán trên Postgres thật.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileDecisionOutboxIntegrationTest extends SellerPostgresTestBase {

    @Autowired
    private SubmitSellerProfile submitSellerProfile;

    @Autowired
    private UpdateShopInfo updateShopInfo;

    @Autowired
    private ApproveSellerProfile approveSellerProfile;

    @Autowired
    private RejectSellerProfile rejectSellerProfile;

    @Autowired
    private GetSellerProfileForReview getSellerProfileForReview;

    @Autowired
    private SellerProfileRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");
    }

    private int outboxRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
        return count != null ? count : 0;
    }

    private UUID preparePendingProfile(UUID owner, String identityNumber, String shopName) {
        SubmitSellerProfile.Result result = submitSellerProfile.handle(submitCommand(user(owner), identityNumber, shopName));
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");
        return result.profile().getId().getValue();
    }

    private long getViewedVersion(UUID adminId, UUID profileId) {
        return getSellerProfileForReview.handle(
                new GetSellerProfileForReview.Query(admin(adminId), profileId)
        ).profile().version();
    }

    @Test
    @DisplayName("Duyệt hồ sơ: bảng outbox_events có đúng 2 hàng, cấu trúc payload và DB đúng hợp đồng")
    void approveWritesTwoOutboxRowsAndUpdatesDatabase() throws Exception {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        ApproveSellerProfile.Command command = new ApproveSellerProfile.Command(admin(adminId), profileId, viewedVersion);
        approveSellerProfile.handle(command);

        assertThat(outboxRows()).isEqualTo(2);

        // Hàng 1: SellerProfileApproved
        Map<String, Object> approvedRow = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE event_type = 'SellerProfileApproved'"
        );
        assertThat(approvedRow.get("routing_key")).isEqualTo("seller.profile.approved");
        assertThat(approvedRow.get("aggregate_id")).isEqualTo(owner.toString());

        String approvedPayloadJson = (String) approvedRow.get("payload");
        JsonNode approvedEnvelopeNode = objectMapper.readTree(approvedPayloadJson);
        JsonNode approvedPayload = approvedEnvelopeNode.get("payload");

        // Approved payload có đúng 4 khóa: accountId, profileId, version, occurredAt
        List<String> approvedFieldNames = new ArrayList<>(approvedPayload.propertyNames());
        assertThat(approvedFieldNames).containsExactlyInAnyOrder("accountId", "profileId", "version", "occurredAt");
        assertThat(approvedPayload.get("version").asLong()).isEqualTo(viewedVersion + 1);

        // Không chứa các khóa nhạy cảm / không thuộc payload
        assertThat(approvedEnvelopeNode.has("shopName")).isFalse();
        assertThat(approvedPayload.has("shopName")).isFalse();
        assertThat(approvedPayload.has("reviewedBy")).isFalse();
        assertThat(approvedPayload.has("viewedVersion")).isFalse();
        assertThat(approvedPayload.has("rejectionReason")).isFalse();
        assertThat(approvedPayload.has("identityNumber")).isFalse();
        assertThat(approvedPayload.has("taxCode")).isFalse();
        assertThat(approvedPayload.has("businessAddress")).isFalse();
        assertThat(approvedPayload.has("legalName")).isFalse();

        // Hàng 2: AuditActionRecorded
        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE event_type = 'AuditActionRecorded'"
        );
        assertThat(auditRow.get("routing_key")).isEqualTo("audit.action.recorded");

        String auditPayloadJson = (String) auditRow.get("payload");
        EventEnvelope auditEnvelope = objectMapper.readValue(auditPayloadJson, EventEnvelope.class);
        AuditRecord auditRecord = AuditRecordReader.read(auditEnvelope);

        assertThat(auditRecord.sourceService()).isEqualTo("seller-service");
        assertThat(auditRecord.action()).isEqualTo("SELLER_PROFILE_APPROVED");
        assertThat(auditRecord.actor()).isEqualTo(new AuditActor(adminId, "ADMIN"));
        assertThat(auditRecord.target()).isEqualTo(new AuditTarget("SELLER_PROFILE", profileId.toString()));

        Map<String, Object> details = auditRecord.details();
        assertThat(details).containsOnlyKeys("viewedVersion", "sellerAccountId");
        assertThat(((Number) details.get("viewedVersion")).longValue()).isEqualTo(viewedVersion);
        assertThat(details.get("sellerAccountId")).isEqualTo(owner.toString());
        assertThat(auditRecord.droppedDetails()).isEmpty();

        // Cơ sở dữ liệu: hồ sơ APPROVED, reviewed_by = quản trị, version = viewedVersion + 1
        SellerProfile updatedProfile = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(updatedProfile.status()).isEqualTo(SellerProfileStatus.APPROVED);
        assertThat(updatedProfile.reviewedBy()).isEqualTo(adminId);
        assertThat(updatedProfile.version()).isEqualTo(viewedVersion + 1);

        Map<String, Object> dbRow = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, version FROM seller_profiles WHERE id = ?", profileId
        );
        assertThat(dbRow.get("status")).isEqualTo("APPROVED");
        assertThat(dbRow.get("reviewed_by")).isEqualTo(adminId);
        assertThat(((Number) dbRow.get("version")).longValue()).isEqualTo(viewedVersion + 1);
    }

    @Test
    @DisplayName("Từ chối hồ sơ: bảng outbox_events có đúng 2 hàng, không rò rỉ lý do vào payload Rejected")
    void rejectWritesTwoOutboxRowsAndUpdatesDatabase() throws Exception {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, viewedVersion, "Giấy tờ mờ"
        );
        rejectSellerProfile.handle(command);

        assertThat(outboxRows()).isEqualTo(2);

        // Hàng 1: SellerProfileRejected
        Map<String, Object> rejectedRow = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE event_type = 'SellerProfileRejected'"
        );
        assertThat(rejectedRow.get("routing_key")).isEqualTo("seller.profile.rejected");

        String rejectedPayloadJson = (String) rejectedRow.get("payload");
        assertThat(rejectedPayloadJson).doesNotContain("Giấy tờ mờ");

        JsonNode rejectedEnvelopeNode = objectMapper.readTree(rejectedPayloadJson);
        JsonNode rejectedPayload = rejectedEnvelopeNode.get("payload");

        List<String> rejectedFieldNames = new ArrayList<>(rejectedPayload.propertyNames());
        assertThat(rejectedFieldNames).containsExactlyInAnyOrder("accountId", "version", "occurredAt");

        // Hàng 2: AuditActionRecorded
        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_events WHERE event_type = 'AuditActionRecorded'"
        );
        assertThat(auditRow.get("routing_key")).isEqualTo("audit.action.recorded");

        String auditPayloadJson = (String) auditRow.get("payload");
        EventEnvelope auditEnvelope = objectMapper.readValue(auditPayloadJson, EventEnvelope.class);
        AuditRecord auditRecord = AuditRecordReader.read(auditEnvelope);

        assertThat(auditRecord.sourceService()).isEqualTo("seller-service");
        assertThat(auditRecord.action()).isEqualTo("SELLER_PROFILE_REJECTED");
        assertThat(auditRecord.actor()).isEqualTo(new AuditActor(adminId, "ADMIN"));
        assertThat(auditRecord.target()).isEqualTo(new AuditTarget("SELLER_PROFILE", profileId.toString()));

        Map<String, Object> details = auditRecord.details();
        assertThat(details).containsOnlyKeys("viewedVersion", "sellerAccountId", "rejectionReason");
        assertThat(((Number) details.get("viewedVersion")).longValue()).isEqualTo(viewedVersion);
        assertThat(details.get("sellerAccountId")).isEqualTo(owner.toString());
        assertThat(details.get("rejectionReason")).isEqualTo("Giấy tờ mờ");

        // Cơ sở dữ liệu: hồ sơ REJECTED với rejection_reason đúng
        SellerProfile updatedProfile = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(updatedProfile.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(updatedProfile.rejectionReason()).isEqualTo("Giấy tờ mờ");

        Map<String, Object> dbRow = jdbcTemplate.queryForMap(
                "SELECT status, rejection_reason, version FROM seller_profiles WHERE id = ?", profileId
        );
        assertThat(dbRow.get("status")).isEqualTo("REJECTED");
        assertThat(dbRow.get("rejection_reason")).isEqualTo("Giấy tờ mờ");
        assertThat(((Number) dbRow.get("version")).longValue()).isEqualTo(viewedVersion + 1);
    }

    @Test
    @DisplayName("Quyết định thất bại không để lại hàng outbox nào: STALE_VERSION, INVALID_STATE, SELLER_ROLE_NOT_ALLOWED")
    void failedDecisionsLeaveZeroOutboxRows() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        // 1. Duyệt với phiên bản đã xem sai (STALE_VERSION)
        assertThatThrownBy(() -> approveSellerProfile.handle(
                new ApproveSellerProfile.Command(admin(adminId), profileId, viewedVersion + 99)
        )).isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.STALE_VERSION));
        assertThat(outboxRows()).isZero();
        assertThat(repository.findById(SellerProfileId.of(profileId)).orElseThrow().status())
                .isEqualTo(SellerProfileStatus.PENDING_REVIEW);

        // 2. Vai USER (SELLER_ROLE_NOT_ALLOWED)
        assertThatThrownBy(() -> approveSellerProfile.handle(
                new ApproveSellerProfile.Command(user(owner), profileId, viewedVersion)
        )).isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED));
        assertThat(outboxRows()).isZero();
        assertThat(repository.findById(SellerProfileId.of(profileId)).orElseThrow().status())
                .isEqualTo(SellerProfileStatus.PENDING_REVIEW);

        // Duyệt thành công lần đầu
        approveSellerProfile.handle(new ApproveSellerProfile.Command(admin(adminId), profileId, viewedVersion));
        assertThat(outboxRows()).isEqualTo(2);
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");

        // 3. Duyệt hồ sơ đã duyệt (INVALID_STATE)
        long currentVersion = repository.findById(SellerProfileId.of(profileId)).orElseThrow().version();
        assertThatThrownBy(() -> approveSellerProfile.handle(
                new ApproveSellerProfile.Command(admin(adminId), profileId, currentVersion)
        )).isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE));
        assertThat(outboxRows()).isZero();
        assertThat(repository.findById(SellerProfileId.of(profileId)).orElseThrow().status())
                .isEqualTo(SellerProfileStatus.APPROVED);
    }

    @Test
    @DisplayName("E16: Sau khi duyệt xong, chủ hồ sơ sửa gian hàng thành công và outbox_events vẫn là 2")
    void approvedProfileCanUpdateShopInfoWithoutEmittingNewEvents() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        // Duyệt hồ sơ
        approveSellerProfile.handle(new ApproveSellerProfile.Command(admin(adminId), profileId, viewedVersion));
        assertThat(outboxRows()).isEqualTo(2);

        // Chủ sửa gian hàng
        UpdateShopInfo.Result updateResult = updateShopInfo.handle(new UpdateShopInfo.Command(
                user(owner),
                Patch.of("Tên Mới Của Gian Hàng"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        ));

        assertThat(updateResult.profile().shopInfo().shopName().display()).isEqualTo("Tên Mới Của Gian Hàng");
        assertThat(updateResult.profile().status()).isEqualTo(SellerProfileStatus.APPROVED);

        // Số hàng outbox_events vẫn là 2
        assertThat(outboxRows()).isEqualTo(2);
    }
}
