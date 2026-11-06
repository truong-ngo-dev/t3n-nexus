package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.NOW;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.actorOf;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.individual;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.shop;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.submit;

/**
 * Kiểm thử tích hợp cơ chế khóa lạc quan chạy ngầm qua Hibernate @Version (KT-08).
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileOptimisticLockIntegrationTest extends SellerPostgresTestBase {

    @Autowired
    private SellerProfileRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");
    }

    @Test
    @DisplayName("Hai luồng sửa cùng một hồ sơ đồng thời thì duy nhất một luồng thắng và luồng kia bị OptimisticLockingFailureException")
    void twoConcurrentEditsExactlyOneWins() throws Exception {
        UUID owner = UUID.randomUUID();
        String cccd = "079123456789";
        SellerProfile p = submit(owner, individual(cccd), shop("Tên Gian Hàng Gốc"));
        transactionTemplate.executeWithoutResult(status -> repository.save(p));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<String> winnerShopName = new AtomicReference<>();

        try {
            Future<Boolean> f1 = pool.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        SellerProfile profile1 = repository.findById(p.getId()).orElseThrow();
                        try {
                            barrier.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        boolean changed = profile1.updateShopInfo(actorOf(owner), shop("Tên Mới Của Luồng 1"), NOW.plusSeconds(10));
                        assertThat(changed).isTrue();
                        repository.save(profile1);
                    });
                    winnerShopName.set("Tên Mới Của Luồng 1");
                    return true;
                } catch (OptimisticLockingFailureException e) {
                    return false;
                }
            });

            Future<Boolean> f2 = pool.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        SellerProfile profile2 = repository.findById(p.getId()).orElseThrow();
                        try {
                            barrier.await();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        boolean changed = profile2.updateShopInfo(actorOf(owner), shop("Tên Mới Của Luồng 2"), NOW.plusSeconds(10));
                        assertThat(changed).isTrue();
                        repository.save(profile2);
                    });
                    winnerShopName.set("Tên Mới Của Luồng 2");
                    return true;
                } catch (OptimisticLockingFailureException e) {
                    return false;
                }
            });

            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            boolean r1 = f1.get();
            boolean r2 = f2.get();

            assertThat(r1 ^ r2).isTrue(); // Đúng 1 luồng thành công và 1 luồng ném OptimisticLockingFailureException

            // Đọc lại từ DB
            SellerProfile updated = repository.findById(p.getId()).orElseThrow();
            assertThat(updated.shopInfo().shopName().display()).isEqualTo(winnerShopName.get());

            Long version = jdbcTemplate.queryForObject(
                    "SELECT version FROM seller_profiles WHERE id = ?",
                    Long.class,
                    p.getId().getValue()
            );
            assertThat(version).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Sửa tuần tự các trường khác nhau thì cả hai thay đổi đều được ghi nhận và version tăng tương ứng")
    void sequentialEditsOfDifferentFieldsAreBothKept() {
        UUID owner = UUID.randomUUID();
        String cccd = "079123456789";
        SellerProfile p = submit(owner, individual(cccd), shop("Tên Cũ"));
        transactionTemplate.executeWithoutResult(status -> repository.save(p));

        // Giao dịch 1: đổi tên gian hàng
        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile loaded1 = repository.findById(p.getId()).orElseThrow();
            boolean changed = loaded1.updateShopInfo(actorOf(owner), shop("Tên Mới Tuần Tự"), NOW.plusSeconds(10));
            assertThat(changed).isTrue();
            repository.save(loaded1);
        });

        // Giao dịch 2 (sau đó): đổi tên pháp lý
        LegalInfo newLegal = LegalInfo.of(
                SellerType.INDIVIDUAL,
                "Nguyễn Văn Bảo",
                null,
                cccd,
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM"
        );
        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile loaded2 = repository.findById(p.getId()).orElseThrow();
            boolean changed = loaded2.updateLegalInfo(actorOf(owner), newLegal, NOW.plusSeconds(20));
            assertThat(changed).isTrue();
            repository.save(loaded2);
        });

        // Đọc lại: cả tên gian hàng mới và tên pháp lý mới đều có; version = 2
        SellerProfile reloaded = repository.findById(p.getId()).orElseThrow();
        assertThat(reloaded.shopInfo().shopName().display()).isEqualTo("Tên Mới Tuần Tự");
        assertThat(reloaded.legalInfo().legalName()).isEqualTo("Nguyễn Văn Bảo");

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?",
                Long.class,
                p.getId().getValue()
        );
        assertThat(version).isEqualTo(2L);
    }

    @Test
    @DisplayName("Giao dịch ghi sau dựa trên dữ liệu cũ bị thất bại với OptimisticLockingFailureException sau khi giao dịch trước đã commit")
    void staleSecondWriterLosesAfterFirstCommitted() {
        UUID owner = UUID.randomUUID();
        String cccd = "079123456789";
        SellerProfile p = submit(owner, individual(cccd), shop("Tên Gốc"));
        transactionTemplate.executeWithoutResult(status -> repository.save(p));

        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Luồng chính mở giao dịch A nạp P (chưa ghi)
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(statusA -> {
            SellerProfile profileA = repository.findById(p.getId()).orElseThrow();

            // Trong lúc đó giao dịch B nạp P, đổi tên gian hàng, lưu, commit
            requiresNewTemplate.executeWithoutResult(statusB -> {
                SellerProfile profileB = repository.findById(p.getId()).orElseThrow();
                profileB.updateShopInfo(actorOf(owner), shop("Tên Đổi Bởi B"), NOW.plusSeconds(10));
                repository.save(profileB);
            });

            // Tiếp tục giao dịch A: updateShopInfo tên khác, save -> ném OptimisticLockingFailureException
            profileA.updateShopInfo(actorOf(owner), shop("Tên Đổi Bởi A"), NOW.plusSeconds(20));
            repository.save(profileA);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        // Giao dịch A rollback; tên trong DB là tên của B
        SellerProfile finalProfile = repository.findById(p.getId()).orElseThrow();
        assertThat(finalProfile.shopInfo().shopName().display()).isEqualTo("Tên Đổi Bởi B");
    }

    @Test
    @DisplayName("Ghi thất bại do khóa lạc quan không để lại dữ liệu rác hay thay đổi từng phần")
    void failedWriterLeavesNoPartialChange() {
        UUID owner = UUID.randomUUID();
        String cccd = "079123456789";
        SellerProfile p = submit(owner, individual(cccd), shop("Tên Gốc"));
        transactionTemplate.executeWithoutResult(status -> repository.save(p));

        TransactionTemplate requiresNewTemplate = new TransactionTemplate(transactionManager);
        requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            transactionTemplate.executeWithoutResult(statusA -> {
                SellerProfile profileA = repository.findById(p.getId()).orElseThrow();

                requiresNewTemplate.executeWithoutResult(statusB -> {
                    SellerProfile profileB = repository.findById(p.getId()).orElseThrow();
                    profileB.updateShopInfo(actorOf(owner), shop("Tên B Hoàn Chỉnh"), NOW.plusSeconds(10));
                    repository.save(profileB);
                });

                profileA.updateShopInfo(actorOf(owner), shop("Tên A Rác"), NOW.plusSeconds(20));
                repository.save(profileA);
            });
        } catch (OptimisticLockingFailureException ignored) {
            // Mong đợi ngoại lệ khóa lạc quan
        }

        // Kiểm tra hàng trong DB: version = 1 và mọi cột khớp bản của B (không lẫn dữ liệu của A)
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT shop_name, version FROM seller_profiles WHERE id = ?",
                p.getId().getValue()
        );
        assertThat(row.get("shop_name")).isEqualTo("Tên B Hoàn Chỉnh");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
    }
}
