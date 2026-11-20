package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.application.sellerprofile.PendingSellerProfileQuery;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.NOW;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.actorOf;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.digits;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.household;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.individual;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.rejected;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.shop;

/**
 * Kiểm thử tích hợp cổng đọc PendingSellerProfileQuery ở tầng lưu trữ.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class PendingSellerProfileQueryIntegrationTest extends SellerPostgresTestBase {

    @Autowired
    private PendingSellerProfileQuery query;

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
    @DisplayName("Chỉ trả hồ sơ PENDING_REVIEW bỏ qua các hồ sơ APPROVED và REJECTED")
    void returnsOnlyPendingReviewProfiles() {
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        UUID owner3 = UUID.randomUUID();
        UUID owner4 = UUID.randomUUID();

        SellerProfile pending1 = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                actorOf(owner1),
                individual(digits(1, 12)),
                shop("Gian hàng 1"),
                UUID.randomUUID(),
                NOW
        );

        SellerProfile pending2 = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                actorOf(owner2),
                individual(digits(2, 12)),
                shop("Gian hàng 2"),
                UUID.randomUUID(),
                NOW.plusSeconds(10)
        );

        SellerProfile approved = SellerProfile.restore(
                SellerProfileId.of(UUID.randomUUID()),
                owner3,
                SellerProfileStatus.APPROVED,
                individual(digits(3, 12)),
                shop("Gian hàng 3"),
                null,
                NOW.minusSeconds(100),
                NOW.minusSeconds(50),
                UUID.randomUUID(),
                NOW.minusSeconds(100),
                NOW.minusSeconds(50),
                1L
        );

        SellerProfile rejectedProfile = rejected(
                owner4,
                individual(digits(4, 12)),
                shop("Gian hàng 4")
        );

        transactionTemplate.executeWithoutResult(status -> {
            repository.save(pending1);
            repository.save(pending2);
            repository.save(approved);
            repository.save(rejectedProfile);
        });

        PendingSellerProfileQuery.Result result = query.find(0, 10);

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
        assertThat(result.items()).extracting(PendingSellerProfileQuery.Item::id)
                .containsExactly(pending1.getId().getValue(), pending2.getId().getValue());
    }

    @Test
    @DisplayName("Sắp xếp theo submittedAt tăng dần, hồ sơ nộp cũ nhất lên trước")
    void sortsBySubmittedAtAscending() {
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        UUID owner3 = UUID.randomUUID();
        UUID owner4 = UUID.randomUUID();

        Instant t1 = NOW.plusSeconds(1000);
        Instant t2 = NOW.plusSeconds(2000);
        Instant t3 = NOW.plusSeconds(3000);
        Instant t4 = NOW.plusSeconds(4000);

        SellerProfile p1 = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                actorOf(owner1),
                individual(digits(1, 12)),
                shop("Gian hàng 1"),
                UUID.randomUUID(),
                t1
        );
        SellerProfile p2 = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                actorOf(owner2),
                individual(digits(2, 12)),
                shop("Gian hàng 2"),
                UUID.randomUUID(),
                t2
        );
        SellerProfile p3 = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                actorOf(owner3),
                individual(digits(3, 12)),
                shop("Gian hàng 3"),
                UUID.randomUUID(),
                t3
        );
        SellerProfile p4 = SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                actorOf(owner4),
                individual(digits(4, 12)),
                shop("Gian hàng 4"),
                UUID.randomUUID(),
                t4
        );

        // Lưu theo thứ tự 3, 1, 4, 2 ngày khác nhau
        transactionTemplate.executeWithoutResult(status -> {
            repository.save(p3);
            repository.save(p1);
            repository.save(p4);
            repository.save(p2);
        });

        PendingSellerProfileQuery.Result result = query.find(0, 10);

        assertThat(result.items()).extracting(PendingSellerProfileQuery.Item::id)
                .containsExactly(
                        p1.getId().getValue(),
                        p2.getId().getValue(),
                        p3.getId().getValue(),
                        p4.getId().getValue()
                );
    }

    @Test
    @DisplayName("Phá hòa theo id tăng dần khi cùng submittedAt")
    void tieBreaksByIdAscending() {
        int count = 20;
        List<UUID> expectedIds = new ArrayList<>();
        List<SellerProfile> profiles = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            UUID id = new UUID(0, i);
            expectedIds.add(id);
            profiles.add(SellerProfile.submit(
                    SellerProfileId.of(id),
                    actorOf(UUID.randomUUID()),
                    individual(digits(i, 12)),
                    shop("Gian hàng " + i),
                    UUID.randomUUID(),
                    NOW
            ));
        }

        // Cùng submittedAt; lưu theo thứ tự id GIẢM dần để thứ tự lưu không trùng thứ tự mong đợi
        transactionTemplate.executeWithoutResult(status -> {
            for (int i = count - 1; i >= 0; i--) {
                repository.save(profiles.get(i));
            }
        });

        PendingSellerProfileQuery.Result result = query.find(0, count);

        assertThat(result.items()).extracting(PendingSellerProfileQuery.Item::id)
                .containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("Phân trang trả đúng số phần tử mỗi trang, totalElements và rỗng khi quá trang")
    void paginationWorksCorrectly() {
        List<UUID> expectedIds = new ArrayList<>();

        transactionTemplate.executeWithoutResult(status -> {
            for (int i = 0; i < 5; i++) {
                UUID id = UUID.randomUUID();
                expectedIds.add(id);
                SellerProfile p = SellerProfile.submit(
                        SellerProfileId.of(id),
                        actorOf(UUID.randomUUID()),
                        individual(digits(i + 1, 12)),
                        shop("Gian hàng " + (i + 1)),
                        UUID.randomUUID(),
                        NOW.plusSeconds(i * 10)
                );
                repository.save(p);
            }
        });

        PendingSellerProfileQuery.Result page0 = query.find(0, 2);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.items()).hasSize(2);

        PendingSellerProfileQuery.Result page1 = query.find(1, 2);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.size()).isEqualTo(2);
        assertThat(page1.totalElements()).isEqualTo(5);
        assertThat(page1.items()).hasSize(2);

        PendingSellerProfileQuery.Result page2 = query.find(2, 2);
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.size()).isEqualTo(2);
        assertThat(page2.totalElements()).isEqualTo(5);
        assertThat(page2.items()).hasSize(1);

        PendingSellerProfileQuery.Result page3 = query.find(3, 2);
        assertThat(page3.page()).isEqualTo(3);
        assertThat(page3.size()).isEqualTo(2);
        assertThat(page3.totalElements()).isEqualTo(5);
        assertThat(page3.items()).isEmpty();

        List<UUID> combined = new ArrayList<>();
        combined.addAll(page0.items().stream().map(PendingSellerProfileQuery.Item::id).toList());
        combined.addAll(page1.items().stream().map(PendingSellerProfileQuery.Item::id).toList());
        combined.addAll(page2.items().stream().map(PendingSellerProfileQuery.Item::id).toList());

        assertThat(combined).containsExactlyElementsOf(expectedIds);
    }

    @Test
    @DisplayName("Không có hồ sơ chờ duyệt thì find trả danh sách rỗng và totalElements bằng 0")
    void emptyQueueReturnsEmptyListAndZeroTotalElements() {
        PendingSellerProfileQuery.Result result = query.find(0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("Nội dung từng Item khớp chính xác với hồ sơ nguồn")
    void itemContentMatchesSourceProfile() {
        UUID owner = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Instant submittedAt = NOW.plusSeconds(123);

        SellerProfile householdProfile = SellerProfile.submit(
                SellerProfileId.of(profileId),
                actorOf(owner),
                household(digits(999, 10)),
                shop("Hộ Kinh Doanh Phát Đạt"),
                UUID.randomUUID(),
                submittedAt
        );

        transactionTemplate.executeWithoutResult(status -> repository.save(householdProfile));

        PendingSellerProfileQuery.Result result = query.find(0, 10);
        assertThat(result.items()).hasSize(1);

        PendingSellerProfileQuery.Item item = result.items().getFirst();
        assertThat(item.id()).isEqualTo(profileId);
        assertThat(item.sellerType()).isEqualTo(SellerType.HOUSEHOLD);
        assertThat(item.shopName()).isEqualTo("Hộ Kinh Doanh Phát Đạt");
        assertThat(item.submittedAt()).isEqualTo(submittedAt);
    }

    @Test
    @DisplayName("Cấu trúc Item là record chỉ có 4 trường đúng thứ tự và không rò rỉ dữ liệu định danh")
    void itemRecordHasExactFourComponentsWithoutLeakingIdentityData() {
        RecordComponent[] components = PendingSellerProfileQuery.Item.class.getRecordComponents();
        List<String> componentNames = Arrays.stream(components)
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames).containsExactly("id", "sellerType", "shopName", "submittedAt");
    }
}
