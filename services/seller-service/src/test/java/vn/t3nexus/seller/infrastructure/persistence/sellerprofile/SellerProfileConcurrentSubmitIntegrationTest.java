package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileUniqueness;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.NOW;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.actorOf;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.digits;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.individual;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.shop;

/**
 * Kiểm thử tích hợp xử lý tranh chấp đồng thời khi nộp hồ sơ (KT-07).
 * <br>Ghi chú: phần "không hàng hộp thư của hồ sơ thua" được kiểm ở bước use case (KT-09), không ở đây.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileConcurrentSubmitIntegrationTest extends SellerPostgresTestBase {

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

    private static class SubmissionOutcome {
        final boolean success;
        final String domainErrorCode;
        final String constraintViolationName;

        private SubmissionOutcome(boolean success, String domainErrorCode, String constraintViolationName) {
            this.success = success;
            this.domainErrorCode = domainErrorCode;
            this.constraintViolationName = constraintViolationName;
        }

        static SubmissionOutcome success() {
            return new SubmissionOutcome(true, null, null);
        }

        static SubmissionOutcome domainFailure(String code) {
            return new SubmissionOutcome(false, code, null);
        }

        static SubmissionOutcome constraintFailure(String constraintName) {
            return new SubmissionOutcome(false, null, constraintName);
        }
    }

    @Test
    @DisplayName("20 luồng cùng nộp hồ sơ cho một chủ sở hữu thì duy nhất một luồng thắng")
    void sameOwnerTwentyThreadsExactlyOneWins() throws Exception {
        UUID owner = UUID.randomUUID();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<SubmissionOutcome>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    startLatch.await();
                    ShopInfo shopInfo = shop("Shop Owner Race " + idx);
                    LegalInfo legalInfo = individual("079" + digits(idx, 9));
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            new SellerProfileUniqueness(repository).requireCanSubmit(owner, shopInfo.shopName(), legalInfo);
                            repository.save(SellerProfile.submit(SellerProfileId.of(UUID.randomUUID()), actorOf(owner), legalInfo, shopInfo, NOW));
                        });
                        return SubmissionOutcome.success();
                    } catch (DomainException de) {
                        return SubmissionOutcome.domainFailure(de.getErrorCode().code());
                    } catch (DataIntegrityViolationException dive) {
                        String msg = NestedExceptionUtils.getMostSpecificCause(dive).getMessage();
                        return SubmissionOutcome.constraintFailure(msg);
                    }
                }));
            }

            startLatch.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            int successCount = 0;
            int failureCount = 0;
            for (Future<SubmissionOutcome> f : futures) {
                SubmissionOutcome outcome = f.get();
                if (outcome.success) {
                    successCount++;
                } else {
                    failureCount++;
                    if (outcome.domainErrorCode != null) {
                        assertThat(outcome.domainErrorCode).isEqualTo("SELLER_PROFILE_EXISTS");
                    } else {
                        assertThat(outcome.constraintViolationName).contains("uq_seller_profiles_owner");
                    }
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(19);

            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
            assertThat(count).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("20 luồng khác chủ nộp cùng tên gian hàng thì duy nhất một luồng thắng")
    void sameShopNameDifferentOwnersExactlyOneWins() throws Exception {
        String[] shopNameVariants = new String[]{"Cửa Hàng Mai", "CUA HANG MAI", "cua hang mai"};
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<SubmissionOutcome>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    startLatch.await();
                    UUID owner = UUID.randomUUID();
                    String shopName = shopNameVariants[idx % shopNameVariants.length];
                    ShopInfo shopInfo = shop(shopName);
                    LegalInfo legalInfo = individual("079" + digits(idx, 9));
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            new SellerProfileUniqueness(repository).requireCanSubmit(owner, shopInfo.shopName(), legalInfo);
                            repository.save(SellerProfile.submit(SellerProfileId.of(UUID.randomUUID()), actorOf(owner), legalInfo, shopInfo, NOW));
                        });
                        return SubmissionOutcome.success();
                    } catch (DomainException de) {
                        return SubmissionOutcome.domainFailure(de.getErrorCode().code());
                    } catch (DataIntegrityViolationException dive) {
                        String msg = NestedExceptionUtils.getMostSpecificCause(dive).getMessage();
                        return SubmissionOutcome.constraintFailure(msg);
                    }
                }));
            }

            startLatch.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            int successCount = 0;
            int failureCount = 0;
            for (Future<SubmissionOutcome> f : futures) {
                SubmissionOutcome outcome = f.get();
                if (outcome.success) {
                    successCount++;
                } else {
                    failureCount++;
                    if (outcome.domainErrorCode != null) {
                        assertThat(outcome.domainErrorCode).isEqualTo("SHOP_NAME_TAKEN");
                    } else {
                        assertThat(outcome.constraintViolationName).contains("uq_seller_profiles_shop_name_key");
                    }
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(19);

            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
            assertThat(count).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("20 luồng khác chủ nộp cùng thông tin định danh hiệu lực thì duy nhất một luồng thắng")
    void sameIdentityKeyDifferentOwnersExactlyOneWins() throws Exception {
        String commonIdentityNumber = "079123456789";
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<SubmissionOutcome>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    startLatch.await();
                    UUID owner = UUID.randomUUID();
                    ShopInfo shopInfo = shop("Shop Identity Race " + idx);
                    LegalInfo legalInfo = individual(commonIdentityNumber);
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            new SellerProfileUniqueness(repository).requireCanSubmit(owner, shopInfo.shopName(), legalInfo);
                            repository.save(SellerProfile.submit(SellerProfileId.of(UUID.randomUUID()), actorOf(owner), legalInfo, shopInfo, NOW));
                        });
                        return SubmissionOutcome.success();
                    } catch (DomainException de) {
                        return SubmissionOutcome.domainFailure(de.getErrorCode().code());
                    } catch (DataIntegrityViolationException dive) {
                        String msg = NestedExceptionUtils.getMostSpecificCause(dive).getMessage();
                        return SubmissionOutcome.constraintFailure(msg);
                    }
                }));
            }

            startLatch.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            int successCount = 0;
            int failureCount = 0;
            for (Future<SubmissionOutcome> f : futures) {
                SubmissionOutcome outcome = f.get();
                if (outcome.success) {
                    successCount++;
                } else {
                    failureCount++;
                    if (outcome.domainErrorCode != null) {
                        assertThat(outcome.domainErrorCode).isEqualTo("IDENTITY_ALREADY_REGISTERED");
                    } else {
                        assertThat(outcome.constraintViolationName).contains("uq_seller_profiles_identity_key_effective");
                    }
                }
            }

            assertThat(successCount).isEqualTo(1);
            assertThat(failureCount).isEqualTo(19);

            Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
            assertThat(count).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
