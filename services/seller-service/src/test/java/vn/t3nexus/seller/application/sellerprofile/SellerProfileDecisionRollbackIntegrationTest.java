package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.t3nexus.lib.audit.AuditRecorder;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.submitCommand;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử tích hợp rollback giao dịch khi ghi kiểm toán thất bại.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileDecisionRollbackIntegrationTest extends SellerPostgresTestBase {

    @Autowired
    private SubmitSellerProfile submitSellerProfile;

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

    @MockitoSpyBean
    private AuditRecorder auditRecorder;

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
    @DisplayName("Ca duyệt: AuditRecorder ném lỗi thì toàn bộ giao dịch rollback, outbox 0 hàng, hồ sơ giữ PENDING_REVIEW")
    void approveRollsBackWhenAuditRecorderThrows() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        doThrow(new IllegalStateException("kiểm toán lỗi"))
                .when(auditRecorder).record(any(), any(), any(), any());

        ApproveSellerProfile.Command command = new ApproveSellerProfile.Command(admin(adminId), profileId, viewedVersion);

        assertThatThrownBy(() -> approveSellerProfile.handle(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("kiểm toán lỗi");

        // Sau đó cơ sở dữ liệu: hồ sơ vẫn PENDING_REVIEW, reviewed_by null, version KHÔNG đổi, outbox 0 hàng
        SellerProfile profile = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(profile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(profile.reviewedBy()).isNull();
        assertThat(profile.version()).isEqualTo(viewedVersion);

        Map<String, Object> dbRow = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, version FROM seller_profiles WHERE id = ?", profileId
        );
        assertThat(dbRow.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(dbRow.get("reviewed_by")).isNull();
        assertThat(((Number) dbRow.get("version")).longValue()).isEqualTo(viewedVersion);

        assertThat(outboxRows()).isZero();
    }

    @Test
    @DisplayName("Ca từ chối: AuditRecorder ném lỗi thì toàn bộ giao dịch rollback, outbox 0 hàng, rejection_reason null")
    void rejectRollsBackWhenAuditRecorderThrows() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        doThrow(new IllegalStateException("kiểm toán lỗi"))
                .when(auditRecorder).record(any(), any(), any(), any());

        RejectSellerProfile.Command command = new RejectSellerProfile.Command(
                admin(adminId), profileId, viewedVersion, "Giấy tờ mờ"
        );

        assertThatThrownBy(() -> rejectSellerProfile.handle(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("kiểm toán lỗi");

        // Sau đó cơ sở dữ liệu: hồ sơ vẫn PENDING_REVIEW, rejection_reason null, reviewed_by null, version KHÔNG đổi
        SellerProfile profile = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(profile.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(profile.rejectionReason()).isNull();
        assertThat(profile.reviewedBy()).isNull();
        assertThat(profile.version()).isEqualTo(viewedVersion);

        Map<String, Object> dbRow = jdbcTemplate.queryForMap(
                "SELECT status, rejection_reason, reviewed_by, version FROM seller_profiles WHERE id = ?", profileId
        );
        assertThat(dbRow.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(dbRow.get("rejection_reason")).isNull();
        assertThat(dbRow.get("reviewed_by")).isNull();
        assertThat(((Number) dbRow.get("version")).longValue()).isEqualTo(viewedVersion);

        assertThat(outboxRows()).isZero();
    }
}
