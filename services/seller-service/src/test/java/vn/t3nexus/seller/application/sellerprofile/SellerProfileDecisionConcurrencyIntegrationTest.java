package vn.t3nexus.seller.application.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.application.sellerprofile.shared.Patch;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.admin;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.submitCommand;
import static vn.t3nexus.seller.application.sellerprofile.UseCaseTestSupport.user;

/**
 * Kiểm thử tích hợp tương tranh khi duyệt hoặc từ chối hồ sơ người bán trên Postgres thật.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileDecisionConcurrencyIntegrationTest extends SellerPostgresTestBase {

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
    @DisplayName("(a) 20 luồng cùng approve hồ sơ với cùng phiên bản đã xem: đúng 1 thành công, 19 luồng ném INVALID_STATE, outbox 2 hàng")
    void twentyThreadsApproveConcurrently() throws Exception {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CyclicBarrier barrier = new CyclicBarrier(threads);
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    approveSellerProfile.handle(new ApproveSellerProfile.Command(admin(adminId), profileId, viewedVersion));
                    return null;
                }));
            }

            int successCount = 0;
            int invalidStateCount = 0;

            for (Future<Void> future : futures) {
                try {
                    future.get(20, TimeUnit.SECONDS);
                    successCount++;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    assertThat(cause).isInstanceOf(DomainException.class);
                    DomainException de = (DomainException) cause;
                    assertThat(de.getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE);
                    invalidStateCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(invalidStateCount).isEqualTo(19);
            assertThat(outboxRows()).isEqualTo(2);

            SellerProfile finalProfile = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
            assertThat(finalProfile.status()).isEqualTo(SellerProfileStatus.APPROVED);
            assertThat(finalProfile.version()).isEqualTo(viewedVersion + 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("(b) 1 luồng duyệt và 1 luồng từ chối cùng lúc: đúng 1 thành công, luồng kia ném INVALID_STATE, outbox 2 hàng")
    void approveAndRejectRace() throws Exception {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");
        long viewedVersion = getViewedVersion(adminId, profileId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);

            Future<String> approveFuture = executor.submit(() -> {
                barrier.await();
                approveSellerProfile.handle(new ApproveSellerProfile.Command(admin(adminId), profileId, viewedVersion));
                return "APPROVED";
            });

            Future<String> rejectFuture = executor.submit(() -> {
                barrier.await();
                rejectSellerProfile.handle(new RejectSellerProfile.Command(admin(adminId), profileId, viewedVersion, "Lý do"));
                return "REJECTED";
            });

            String winner = null;
            int successCount = 0;

            try {
                winner = approveFuture.get(20, TimeUnit.SECONDS);
                successCount++;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                assertThat(cause).isInstanceOf(DomainException.class);
                assertThat(((DomainException) cause).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE);
            }

            try {
                String r = rejectFuture.get(20, TimeUnit.SECONDS);
                winner = r;
                successCount++;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                assertThat(cause).isInstanceOf(DomainException.class);
                assertThat(((DomainException) cause).getErrorCode()).isEqualTo(SellerErrorCode.INVALID_STATE);
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(outboxRows()).isEqualTo(2);

            SellerProfile finalProfile = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
            if ("APPROVED".equals(winner)) {
                assertThat(finalProfile.status()).isEqualTo(SellerProfileStatus.APPROVED);
            } else {
                assertThat(finalProfile.status()).isEqualTo(SellerProfileStatus.REJECTED);
                assertThat(finalProfile.rejectionReason()).isEqualTo("Lý do");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("(c) Kịch bản R4 (S7): quản trị xem v; chủ sửa gian hàng thành v+1; quản trị duyệt v bị STALE_VERSION; xem lại v+1 rồi duyệt thành công")
    void staleVersionScenarioR4() {
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID profileId = preparePendingProfile(owner, "079123456789", "Gian Hàng Hoa Tươi");

        // Quản trị xem, lấy phiên bản v
        long v = getViewedVersion(adminId, profileId);

        // Chủ hồ sơ sửa gian hàng bằng updateShopInfo -> phiên bản thành v + 1
        updateShopInfo.handle(new UpdateShopInfo.Command(
                user(owner),
                Patch.of("Gian Hàng Đổi Tên"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        ));
        SellerProfile afterUpdate = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(afterUpdate.version()).isEqualTo(v + 1);

        // Quản trị duyệt với v -> DomainException mã STALE_VERSION
        assertThatThrownBy(() -> approveSellerProfile.handle(
                new ApproveSellerProfile.Command(admin(adminId), profileId, v)
        )).isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode()).isEqualTo(SellerErrorCode.STALE_VERSION));

        // Hồ sơ vẫn PENDING_REVIEW, outbox_events 0 hàng
        SellerProfile afterStale = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(afterStale.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(outboxRows()).isZero();

        // Xem lại (phiên bản v + 1) rồi duyệt thành công với v + 1
        long newV = getViewedVersion(adminId, profileId);
        assertThat(newV).isEqualTo(v + 1);

        approveSellerProfile.handle(new ApproveSellerProfile.Command(admin(adminId), profileId, newV));

        SellerProfile finalProfile = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
        assertThat(finalProfile.status()).isEqualTo(SellerProfileStatus.APPROVED);
        assertThat(finalProfile.version()).isEqualTo(newV + 1);
        assertThat(outboxRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("(d) Cuộc đua duyệt và chủ sửa gian hàng cùng lúc (5 lần lặp, mỗi lần profile mới): bất biến được bảo đảm")
    void raceApproveAndShopUpdateRepeated() throws Exception {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        for (int iter = 1; iter <= 5; iter++) {
            UUID owner = UUID.nameUUIDFromBytes(("owner-iter-" + iter).getBytes());
            String identity = String.format("079%09d", iter);
            String shopName = "Gian Hàng Iter " + iter;
            UUID profileId = preparePendingProfile(owner, identity, shopName);
            long v = getViewedVersion(adminId, profileId);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CyclicBarrier barrier = new CyclicBarrier(2);

                Future<Boolean> approveFuture = executor.submit(() -> {
                    barrier.await();
                    approveSellerProfile.handle(new ApproveSellerProfile.Command(admin(adminId), profileId, v));
                    return true;
                });

                final int iteration = iter;
                Future<Boolean> updateFuture = executor.submit(() -> {
                    barrier.await();
                    updateShopInfo.handle(new UpdateShopInfo.Command(
                            user(owner),
                            Patch.of("Gian Hàng Đã Sửa " + iteration),
                            Patch.absent(),
                            Patch.absent(),
                            Patch.absent(),
                            Patch.absent()
                    ));
                    return true;
                });

                boolean approveSuccess = false;
                Throwable approveError = null;
                try {
                    approveSuccess = approveFuture.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    approveError = e.getCause();
                }

                boolean updateSuccess = false;
                Throwable updateError = null;
                try {
                    updateSuccess = updateFuture.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    updateError = e.getCause();
                }

                // Không bao giờ cả hai cùng thất bại
                assertThat(approveSuccess || updateSuccess).isTrue();

                if (approveSuccess) {
                    // Nếu duyệt thành công thì hồ sơ APPROVED và outbox_events có đúng 2 hàng
                    SellerProfile p = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
                    assertThat(p.status()).isEqualTo(SellerProfileStatus.APPROVED);
                    assertThat(outboxRows()).isEqualTo(2);
                } else {
                    // Nếu duyệt thất bại thì lỗi chỉ có thể là DomainException mã STALE_VERSION
                    // và hồ sơ vẫn PENDING_REVIEW với 0 hàng
                    assertThat(approveError).isInstanceOf(DomainException.class);
                    assertThat(((DomainException) approveError).getErrorCode()).isEqualTo(SellerErrorCode.STALE_VERSION);
                    SellerProfile p = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
                    assertThat(p.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
                    assertThat(outboxRows()).isZero();
                }

                if (!updateSuccess) {
                    // Lỗi của luồng chủ sửa (nếu có) chỉ có thể là OptimisticLockingFailureException
                    assertThat(updateError).isInstanceOf(OptimisticLockingFailureException.class);
                }

                int successOps = (approveSuccess ? 1 : 0) + (updateSuccess ? 1 : 0);
                SellerProfile finalP = repository.findById(SellerProfileId.of(profileId)).orElseThrow();
                assertThat(finalP.version()).isEqualTo(v + successOps);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
