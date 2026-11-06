package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileApproved;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRejected;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.NOW;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.digits;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.individual;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.shop;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.submit;

/**
 * Kiểm thử tích hợp quyết định duyệt và từ chối hồ sơ người bán ở tầng lưu trữ.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileDecisionPersistenceIntegrationTest extends SellerPostgresTestBase {

    @Autowired
    private SellerProfileRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private final SellerActor admin = new SellerActor(UUID.randomUUID(), "ADMIN");

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");
    }

    @Test
    @DisplayName("findByIdForUpdate trong giao dịch trả hồ sơ có version khớp cột version; mã không tồn tại trả rỗng")
    void findByIdForUpdateInTransactionReturnsProfileMatchingVersionOrEmpty() {
        UUID owner = UUID.randomUUID();
        SellerProfile original = submit(owner, individual(digits(1, 12)), shop("Gian hàng 1"));
        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        transactionTemplate.executeWithoutResult(status -> {
            Optional<SellerProfile> foundOpt = repository.findByIdForUpdate(original.getId());
            assertThat(foundOpt).isPresent();
            SellerProfile found = foundOpt.get();

            Long dbVersion = jdbcTemplate.queryForObject(
                    "SELECT version FROM seller_profiles WHERE id = ?",
                    Long.class,
                    original.getId().getValue()
            );
            assertThat(found.version()).isEqualTo(dbVersion);
        });

        transactionTemplate.executeWithoutResult(status -> {
            Optional<SellerProfile> notFound = repository.findByIdForUpdate(SellerProfileId.of(UUID.randomUUID()));
            assertThat(notFound).isEmpty();
        });
    }

    @Test
    @DisplayName("Gọi findByIdForUpdate ngoài giao dịch ném IllegalTransactionStateException")
    void findByIdForUpdateWithoutTransactionThrowsIllegalTransactionStateException() {
        SellerProfileId id = SellerProfileId.of(UUID.randomUUID());
        assertThatThrownBy(() -> repository.findByIdForUpdate(id))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("Duyệt hồ sơ cập nhật trạng thái APPROVED, thông tin duyệt, tăng version và khớp domain event")
    void approveProfileUpdatesStatusAndIncrementsVersionMatchingEvent() {
        UUID owner = UUID.randomUUID();
        SellerProfile original = submit(owner, individual(digits(2, 12)), shop("Gian hàng 2"));
        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        UUID eventId = UUID.randomUUID();
        AtomicReference<SellerProfileApproved> approvedEventRef = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile profile = repository.findByIdForUpdate(original.getId()).orElseThrow();
            Long viewedVersion = profile.version();
            profile.approve(admin, viewedVersion, eventId, NOW.plusSeconds(60));
            repository.save(profile);

            assertThat(profile.getDomainEvents()).hasSize(1);
            assertThat(profile.getDomainEvents().iterator().next()).isInstanceOf(SellerProfileApproved.class);
            approvedEventRef.set((SellerProfileApproved) profile.getDomainEvents().iterator().next());
        });

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, reviewed_at, rejection_reason, version FROM seller_profiles WHERE id = ?",
                original.getId().getValue()
        );

        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat((UUID) row.get("reviewed_by")).isEqualTo(admin.accountId());
        assertThat(row.get("reviewed_at")).isNotNull();
        assertThat(row.get("rejection_reason")).isNull();

        Long dbVersion = ((Number) row.get("version")).longValue();
        assertThat(dbVersion).isEqualTo(1L);

        SellerProfileApproved event = approvedEventRef.get();
        assertThat(event).isNotNull();
        assertThat(dbVersion).isEqualTo(event.version());
    }

    @Test
    @DisplayName("Từ chối hồ sơ cập nhật trạng thái REJECTED, lý do, thông tin duyệt, tăng version và khớp domain event")
    void rejectProfileUpdatesStatusAndIncrementsVersionMatchingEvent() {
        UUID owner = UUID.randomUUID();
        SellerProfile original = submit(owner, individual(digits(3, 12)), shop("Gian hàng 3"));
        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        UUID eventId = UUID.randomUUID();
        String reason = "Giấy tờ mờ";
        AtomicReference<SellerProfileRejected> rejectedEventRef = new AtomicReference<>();

        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile profile = repository.findByIdForUpdate(original.getId()).orElseThrow();
            Long viewedVersion = profile.version();
            profile.reject(admin, viewedVersion, reason, eventId, NOW.plusSeconds(60));
            repository.save(profile);

            assertThat(profile.getDomainEvents()).hasSize(1);
            assertThat(profile.getDomainEvents().iterator().next()).isInstanceOf(SellerProfileRejected.class);
            rejectedEventRef.set((SellerProfileRejected) profile.getDomainEvents().iterator().next());
        });

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, reviewed_by, reviewed_at, rejection_reason, version FROM seller_profiles WHERE id = ?",
                original.getId().getValue()
        );

        assertThat(row.get("status")).isEqualTo("REJECTED");
        assertThat(row.get("rejection_reason")).isEqualTo("Giấy tờ mờ");
        assertThat((UUID) row.get("reviewed_by")).isEqualTo(admin.accountId());
        assertThat(row.get("reviewed_at")).isNotNull();

        Long dbVersion = ((Number) row.get("version")).longValue();
        assertThat(dbVersion).isEqualTo(1L);

        SellerProfileRejected event = rejectedEventRef.get();
        assertThat(event).isNotNull();
        assertThat(dbVersion).isEqualTo(event.version());
    }

    @Test
    @DisplayName("Khóa dòng chặn giao dịch thứ hai cho đến khi giao dịch thứ nhất commit")
    void rowLockBlocksSecondTransactionUntilFirstCommits() throws Exception {
        UUID owner = UUID.randomUUID();
        SellerProfile original = submit(owner, individual(digits(4, 12)), shop("Gian hàng 4"));
        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);
        AtomicBoolean threadBCompleted = new AtomicBoolean(false);
        AtomicReference<SellerProfile> threadBLoadedProfile = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // Luồng A: mở giao dịch, findByIdForUpdate, approve, save, báo hiệu và chờ nhả trước khi commit
            Future<?> futureA = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    SellerProfile profileA = repository.findByIdForUpdate(original.getId()).orElseThrow();
                    profileA.approve(admin, profileA.version(), UUID.randomUUID(), NOW.plusSeconds(60));
                    repository.save(profileA);
                    lockAcquiredLatch.countDown();
                    try {
                        boolean released = releaseLockLatch.await(10, TimeUnit.SECONDS);
                        if (!released) {
                            throw new IllegalStateException("Hết thời gian chờ nhả khóa");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            });

            // Luồng chính chờ A giữ khóa
            assertThat(lockAcquiredLatch.await(10, TimeUnit.SECONDS)).isTrue();

            // Chạy luồng B mở giao dịch gọi findByIdForUpdate cùng hồ sơ
            Future<?> futureB = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    SellerProfile profileB = repository.findByIdForUpdate(original.getId()).orElseThrow();
                    threadBLoadedProfile.set(profileB);
                });
                threadBCompleted.set(true);
            });

            // Sau khoảng 700 ms luồng B vẫn chưa trả về (bị khóa dòng chặn)
            Thread.sleep(700);
            assertThat(threadBCompleted.get()).isFalse();

            // Nhả tín hiệu cho A commit
            releaseLockLatch.countDown();

            // Đợi cả hai luồng hoàn thành trong tối đa 10 giây
            futureA.get(10, TimeUnit.SECONDS);
            futureB.get(10, TimeUnit.SECONDS);

            assertThat(threadBCompleted.get()).isTrue();
            SellerProfile profileReadByB = threadBLoadedProfile.get();
            assertThat(profileReadByB).isNotNull();
            assertThat(profileReadByB.status()).isEqualTo(SellerProfileStatus.APPROVED);
            assertThat(profileReadByB.version()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Khóa định danh được nhả sau khi hồ sơ trước bị từ chối")
    void identityKeyReleasedAfterRejection() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String identityKey = digits(5, 12);

        SellerProfile profileA = submit(ownerA, individual(identityKey), shop("Gian hàng A"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileA));

        SellerProfile profileB = submit(ownerB, individual(identityKey), shop("Gian hàng B khác"));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> repository.save(profileB)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Từ chối hồ sơ A trong giao dịch
        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile loadedA = repository.findByIdForUpdate(profileA.getId()).orElseThrow();
            loadedA.reject(admin, loadedA.version(), "Lý do từ chối", UUID.randomUUID(), NOW.plusSeconds(60));
            repository.save(loadedA);
        });

        // Lưu hồ sơ B thành công sau khi A bị từ chối
        transactionTemplate.executeWithoutResult(status -> repository.save(profileB));

        Optional<SellerProfile> savedB = repository.findById(profileB.getId());
        assertThat(savedB).isPresent();
    }

    @Test
    @DisplayName("Chỉ mục một phần V3 tồn tại và chứa đúng cột và điều kiện")
    void partialIndexV3ExistsWithExpectedColumnsAndCondition() {
        String indexDef = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_seller_profiles_pending_queue'",
                String.class
        );

        assertThat(indexDef).isNotNull();
        assertThat(indexDef).contains("submitted_at");
        assertThat(indexDef).contains("id");
        assertThat(indexDef).contains("PENDING_REVIEW");
    }
}
