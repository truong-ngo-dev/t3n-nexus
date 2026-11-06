package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.NOW;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.actorOf;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.business;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.digits;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.household;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.individual;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.rejected;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.shop;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.shopWithContactAddress;
import static vn.t3nexus.seller.infrastructure.persistence.sellerprofile.SellerProfileSamples.submit;

/**
 * Kiểm thử tích hợp kho lưu trữ SellerProfileRepositoryAdapter (KT-06).
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileRepositoryAdapterIntegrationTest extends SellerPostgresTestBase {

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
    @DisplayName("Lưu rồi đọc lại hồ sơ cá nhân trả đầy đủ các trường khớp bản gốc")
    void saveThenFindByIdRoundTripsIndividual() {
        UUID owner = UUID.randomUUID();
        String cccd = "079" + digits(123456789, 9);
        LegalInfo legal = individual(cccd);
        ShopInfo shopInfo = shop("Cửa Hàng Hoa Sen");
        SellerProfile original = submit(owner, legal, shopInfo);

        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        // Đọc ngoài giao dịch
        Optional<SellerProfile> foundOpt = repository.findById(original.getId());
        assertThat(foundOpt).isPresent();
        SellerProfile found = foundOpt.get();

        assertThat(found.getId()).isEqualTo(original.getId());
        assertThat(found.ownerAccountId()).isEqualTo(original.ownerAccountId());
        assertThat(found.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(found.legalInfo()).isEqualTo(original.legalInfo());
        assertThat(found.shopInfo()).isEqualTo(original.shopInfo());
        assertThat(found.submittedAt()).isEqualTo(original.submittedAt());
        assertThat(found.createdAt()).isEqualTo(original.createdAt());
        assertThat(found.updatedAt()).isEqualTo(original.updatedAt());
        assertThat(found.rejectionReason()).isNull();
        assertThat(found.reviewedAt()).isNull();
        assertThat(found.reviewedBy()).isNull();
    }

    @Test
    @DisplayName("Lưu rồi đọc lại hồ sơ hộ kinh doanh có mã số thuế")
    void roundTripsHousehold() {
        UUID owner = UUID.randomUUID();
        String taxCode = digits(1234567890, 10);
        LegalInfo legal = household(taxCode);
        ShopInfo shopInfo = shopWithContactAddress("Hộ Kinh Doanh Bình Minh");
        SellerProfile original = submit(owner, legal, shopInfo);

        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        Optional<SellerProfile> foundOpt = repository.findById(original.getId());
        assertThat(foundOpt).isPresent();
        SellerProfile found = foundOpt.get();

        assertThat(found.legalInfo()).isEqualTo(legal);
        assertThat(found.legalInfo().identityKey()).isEqualTo(taxCode);
        assertThat(found.shopInfo()).isEqualTo(shopInfo);
    }

    @Test
    @DisplayName("Lưu rồi đọc lại hồ sơ doanh nghiệp có đại diện và mã số thuế")
    void roundTripsBusiness() {
        UUID owner = UUID.randomUUID();
        String taxCode = digits(987654321, 10);
        LegalInfo legal = business(taxCode);
        ShopInfo shopInfo = shopWithContactAddress("Công ty Sao Mai");
        SellerProfile original = submit(owner, legal, shopInfo);

        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        Optional<SellerProfile> foundOpt = repository.findById(original.getId());
        assertThat(foundOpt).isPresent();
        SellerProfile found = foundOpt.get();

        assertThat(found.legalInfo()).isEqualTo(legal);
        assertThat(found.legalInfo().identityKey()).isEqualTo(taxCode);
        assertThat(found.shopInfo()).isEqualTo(shopInfo);
    }

    @Test
    @DisplayName("Tìm kiếm theo chủ sở hữu và theo ID trả đúng hồ sơ hoặc rỗng khi không tồn tại")
    void findByOwnerReturnsSameProfile() {
        UUID owner = UUID.randomUUID();
        SellerProfile original = submit(owner, individual("079123456789"), shop("Shop Owner Check"));
        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        Optional<SellerProfile> foundOpt = repository.findByOwner(owner);
        assertThat(foundOpt).isPresent();
        assertThat(foundOpt.get().getId()).isEqualTo(original.getId());
    }

    @Test
    @DisplayName("Tìm kiếm theo chủ sở hữu không tồn tại trả rỗng")
    void findByOwnerUnknownIsEmpty() {
        Optional<SellerProfile> foundOpt = repository.findByOwner(UUID.randomUUID());
        assertThat(foundOpt).isEmpty();
    }

    @Test
    @DisplayName("Tìm kiếm theo ID không tồn tại trả rỗng")
    void findByIdUnknownIsEmpty() {
        Optional<SellerProfile> foundOpt = repository.findById(SellerProfileId.of(UUID.randomUUID()));
        assertThat(foundOpt).isEmpty();
    }

    @Test
    @DisplayName("existsByOwner chỉ trả đúng cho đúng chủ sở hữu đó")
    void existsByOwnerTrueOnlyForThatOwner() {
        UUID owner = UUID.randomUUID();
        SellerProfile original = submit(owner, individual("079123456789"), shop("Shop Exists Check"));
        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        assertThat(repository.existsByOwner(owner)).isTrue();
        assertThat(repository.existsByOwner(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("Kiểm tra trùng tên gian hàng bỏ qua hồ sơ của chính mình")
    void existsShopNameKeyUsedByOtherIgnoresSelf() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        SellerProfile profileA = submit(ownerA, individual("079123456789"), shop("Cửa Hàng Hoa"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileA));

        // existsShopNameKeyUsedByOther("cua hang hoa", ownerB) đúng
        assertThat(repository.existsShopNameKeyUsedByOther("cua hang hoa", ownerB)).isTrue();
        // (..., ownerA) sai vì bỏ qua chính chủ
        assertThat(repository.existsShopNameKeyUsedByOther("cua hang hoa", ownerA)).isFalse();
        // tên khóa khác sai
        assertThat(repository.existsShopNameKeyUsedByOther("cua hang khac", ownerB)).isFalse();
    }

    @Test
    @DisplayName("Kiểm tra trùng khóa định danh hiệu lực bỏ qua chính chủ và hồ sơ bị từ chối")
    void existsEffectiveIdentityKeyUsedByOtherIgnoresSelfAndRejected() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String keyA = "079" + digits(111111111, 9);
        SellerProfile profileA = submit(ownerA, individual(keyA), shop("Shop A"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileA));

        // A hiệu lực khóa K: (K, B) đúng, (K, A) sai
        assertThat(repository.existsEffectiveIdentityKeyUsedByOther(keyA, ownerB)).isTrue();
        assertThat(repository.existsEffectiveIdentityKeyUsedByOther(keyA, ownerA)).isFalse();

        // Sau khi lưu hồ sơ của C REJECTED với khóa K2, (K2, B) sai (hồ sơ bị từ chối không tính)
        UUID ownerC = UUID.randomUUID();
        String keyC = "079" + digits(222222222, 9);
        SellerProfile profileC = rejected(ownerC, individual(keyC), shop("Shop C"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileC));

        assertThat(repository.existsEffectiveIdentityKeyUsedByOther(keyC, ownerB)).isFalse();
    }

    @Test
    @DisplayName("Hồ sơ bị từ chối đọc lên khớp đầy đủ các trường kiểm duyệt")
    void rejectedProfileRoundTripsReviewFields() {
        UUID owner = UUID.randomUUID();
        SellerProfile original = rejected(owner, individual("079123456789"), shop("Shop Rejected Roundtrip"));
        transactionTemplate.executeWithoutResult(status -> repository.save(original));

        Optional<SellerProfile> foundOpt = repository.findById(original.getId());
        assertThat(foundOpt).isPresent();
        SellerProfile found = foundOpt.get();

        assertThat(found.status()).isEqualTo(SellerProfileStatus.REJECTED);
        assertThat(found.rejectionReason()).isEqualTo("Giấy tờ không rõ");
        assertThat(found.reviewedAt()).isNotNull().isEqualTo(original.reviewedAt());
        assertThat(found.reviewedBy()).isNotNull().isEqualTo(original.reviewedBy());
    }

    @Test
    @DisplayName("Trùng chủ sở hữu bị chặn bởi ràng buộc uq_seller_profiles_owner và hồ sơ cũ còn nguyên")
    void duplicateOwnerRejectedByStorage() {
        UUID owner = UUID.randomUUID();
        SellerProfile profile1 = submit(owner, individual("079" + digits(1, 9)), shop("Shop 1"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profile1));

        SellerProfile profile2 = submit(owner, individual("079" + digits(2, 9)), shop("Shop 2"));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> repository.save(profile2)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_owner"));

        // Hồ sơ 1 vẫn còn nguyên
        Optional<SellerProfile> checkProfile1 = repository.findById(profile1.getId());
        assertThat(checkProfile1).isPresent();
    }

    @Test
    @DisplayName("Trùng tên gian hàng chuẩn hóa bị chặn bởi uq_seller_profiles_shop_name_key")
    void duplicateShopNameKeyRejectedByStorage() {
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        SellerProfile profile1 = submit(owner1, individual("079" + digits(1, 9)), shop("Cửa Hàng Hoa"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profile1));

        SellerProfile profile2 = submit(owner2, individual("079" + digits(2, 9)), shop("CUA HANG HOA"));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> repository.save(profile2)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_shop_name_key"));
    }

    @Test
    @DisplayName("Khóa định danh có thể tái sử dụng khi hồ sơ trước đó đã bị từ chối")
    void identityKeyReusableAfterRejection() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String key = "079" + digits(123456789, 9);

        // A REJECTED khóa K
        SellerProfile profileA = rejected(ownerA, individual(key), shop("Shop A"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileA));

        // B nộp mới cùng khóa K lưu ĐƯỢC
        SellerProfile profileB = submit(ownerB, individual(key), shop("Shop B"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileB));

        assertThat(repository.findById(profileB.getId())).isPresent();
    }

    @Test
    @DisplayName("Khóa định danh bị chặn khi đang có hồ sơ hiệu lực")
    void identityKeyBlockedWhileEffective() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String key = "079" + digits(123456789, 9);

        // A hiệu lực khóa K
        SellerProfile profileA = submit(ownerA, individual(key), shop("Shop A"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileA));

        // B lưu cùng khóa K bị uq_seller_profiles_identity_key_effective
        SellerProfile profileB = submit(ownerB, individual(key), shop("Shop B"));
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> repository.save(profileB)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_identity_key_effective"));
    }

    @Test
    @DisplayName("Nộp lại hồ sơ bị chặn nếu khóa định danh đã bị người khác sử dụng")
    void resubmitBlockedWhenKeyTakenByAnother() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        String key = "079" + digits(123456789, 9);

        // A REJECTED khóa K
        SellerProfile profileA = rejected(ownerA, individual(key), shop("Shop A"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileA));

        // B nộp mới khóa K thành công
        SellerProfile profileB = submit(ownerB, individual(key), shop("Shop B"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileB));

        // Trong một giao dịch nạp A, resubmit, save -> bị uq_seller_profiles_identity_key_effective
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            SellerProfile a = repository.findById(profileA.getId()).orElseThrow();
            a.resubmit(actorOf(ownerA), a.legalInfo(), a.shopInfo(), NOW.plusSeconds(3600));
            repository.save(a);
        })).isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                        .contains("uq_seller_profiles_identity_key_effective"));

        // Hồ sơ A trong DB vẫn REJECTED
        SellerProfile checkA = repository.findById(profileA.getId()).orElseThrow();
        assertThat(checkA.status()).isEqualTo(SellerProfileStatus.REJECTED);
    }

    @Test
    @DisplayName("Nộp lại hồ sơ thành công khi khóa định danh tự do")
    void resubmitAllowedWhenKeyFree() {
        UUID ownerA = UUID.randomUUID();
        String key = "079" + digits(123456789, 9);
        SellerProfile profileA = rejected(ownerA, individual(key), shop("Shop A"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profileA));

        // Nạp A, resubmit, lưu
        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile a = repository.findById(profileA.getId()).orElseThrow();
            a.resubmit(actorOf(ownerA), a.legalInfo(), a.shopInfo(), NOW.plusSeconds(3600));
            repository.save(a);
        });

        // Đọc lại
        SellerProfile reloaded = repository.findById(profileA.getId()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(SellerProfileStatus.PENDING_REVIEW);
        assertThat(reloaded.rejectionReason()).isNull();
        assertThat(reloaded.submittedAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    @DisplayName("Phiên bản khởi đầu bằng 0 và được giấu khỏi Aggregate")
    void versionStartsAtZeroAndIsHiddenFromAggregate() {
        UUID owner = UUID.randomUUID();
        SellerProfile profile = submit(owner, individual("079123456789"), shop("Shop Version Zero"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profile));

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?",
                Long.class,
                profile.getId().getValue()
        );
        assertThat(version).isEqualTo(0L);
    }

    @Test
    @DisplayName("Phiên bản chỉ tăng khi nội dung thực sự thay đổi")
    void versionIncreasesOnlyWhenContentChanges() {
        UUID owner = UUID.randomUUID();
        SellerProfile profile = submit(owner, individual("079123456789"), shop("Tên Cũ"));
        transactionTemplate.executeWithoutResult(status -> repository.save(profile));

        // Nạp trong giao dịch, updateShopInfo đổi tên, lưu -> version thành 1
        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile loaded = repository.findById(profile.getId()).orElseThrow();
            boolean changed = loaded.updateShopInfo(actorOf(owner), shop("Tên Mới"), NOW.plusSeconds(10));
            assertThat(changed).isTrue();
            repository.save(loaded);
        });

        Long v1 = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?",
                Long.class,
                profile.getId().getValue()
        );
        assertThat(v1).isEqualTo(1L);

        // Nạp lại trong giao dịch mới và LƯU NGAY không đổi gì -> version vẫn 1
        transactionTemplate.executeWithoutResult(status -> {
            SellerProfile loadedAgain = repository.findById(profile.getId()).orElseThrow();
            repository.save(loadedAgain);
        });

        Long v2 = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?",
                Long.class,
                profile.getId().getValue()
        );
        assertThat(v2).isEqualTo(1L);
    }

    @Test
    @DisplayName("Lưu ngoài giao dịch ném IllegalTransactionStateException và bảng vẫn rỗng")
    void saveWithoutTransactionFails() {
        SellerProfile profile = submit(UUID.randomUUID(), individual("079123456789"), shop("Shop No Tx"));

        assertThatThrownBy(() -> repository.save(profile))
                .isInstanceOf(IllegalTransactionStateException.class);

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Thao tác xóa không được hỗ trợ và ném UnsupportedOperationException")
    void deleteIsUnsupported() {
        assertThatThrownBy(() -> repository.delete(SellerProfileId.of(UUID.randomUUID())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Lưu thất bại trong giao dịch sẽ rollback toàn bộ không để lại hàng dữ liệu")
    void failedSaveLeavesNoRow() {
        UUID owner = UUID.randomUUID();
        SellerProfile h1 = submit(owner, individual("079" + digits(1, 9)), shop("Shop 1"));
        SellerProfile h2 = submit(owner, individual("079" + digits(2, 9)), shop("Shop 2"));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            repository.save(h1);
            repository.save(h2); // Trùng chủ -> lỗi
        })).isInstanceOf(DataIntegrityViolationException.class);

        // Bảng không có H1 (rollback trọn)
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        assertThat(count).isEqualTo(0);
    }
}
