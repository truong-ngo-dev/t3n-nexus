package vn.t3nexus.seller.application.sellerprofile;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.outbox.OutboxEventStore;
import vn.t3nexus.seller.SellerPostgresTestBase;
import vn.t3nexus.seller.SellerServiceApplication;
import vn.t3nexus.seller.application.sellerprofile.shared.Patch;
import vn.t3nexus.seller.domain.sellerprofile.SellerErrorCode;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileSubmitted;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
 * Kiểm thử tích hợp hộp thư outbox của hồ sơ người bán trên Postgres thật (KT-09, KT-07, KT-11).
 * <br>Ghi chú: phần rò dữ liệu qua log (KT-12) và API (KT-10) làm ở bước API.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.main.web-application-type=none"
})
class SellerProfileOutboxIntegrationTest extends SellerPostgresTestBase {

    @Autowired
    private SubmitSellerProfile submitSellerProfile;

    @Autowired
    private ResubmitSellerProfile resubmitSellerProfile;

    @Autowired
    private UpdateShopInfo updateShopInfo;

    @Autowired
    private UpdateLegalInfo updateLegalInfo;

    @Autowired
    private GetMySellerProfile getMySellerProfile;

    @Autowired
    private OutboxEventStore outboxEventStore;

    @Autowired
    private SellerProfileRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE seller_profiles");
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");
    }

    private int outboxRows() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
        return count != null ? count : 0;
    }

    private JsonNode lastEnvelope() {
        String payloadJson = jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox_events ORDER BY id DESC LIMIT 1",
                String.class
        );
        assertThat(payloadJson).isNotNull();
        return objectMapper.readTree(payloadJson);
    }

    @Test
    @DisplayName("Nộp hồ sơ thành công ghi đúng một hàng outbox khớp dữ liệu và không rò rỉ trường nhạy cảm")
    void submitWritesExactlyOneMatchingOutboxRow() {
        UUID ownerA = UUID.randomUUID();
        String identityNumber = "079123456789";
        String shopName = "Gian Hàng Sách Trẻ";
        SubmitSellerProfile.Command cmd = submitCommand(user(ownerA), identityNumber, shopName);

        SubmitSellerProfile.Result result = submitSellerProfile.handle(cmd);
        UUID createdProfileId = result.profile().getId().getValue();

        assertThat(outboxRows()).isEqualTo(1);

        // Kiểm tra các cột bảng outbox_events
        jdbcTemplate.query("SELECT aggregate_type, aggregate_id, event_type, routing_key, event_id, payload FROM outbox_events", rs -> {
            assertThat(rs.getString("aggregate_type")).isEqualTo("SellerProfile");
            assertThat(rs.getString("aggregate_id")).isEqualTo(ownerA.toString());
            assertThat(rs.getString("event_type")).isEqualTo("SellerProfileSubmitted");
            assertThat(rs.getString("routing_key")).isEqualTo("seller.profile.submitted");

            String eventId = rs.getString("event_id");
            String rawPayload = rs.getString("payload");

            // Khung JSON trong cột payload
            JsonNode envelope = objectMapper.readTree(rawPayload);
            assertThat(envelope.get("eventId").asString()).isEqualTo(eventId);
            assertThat(envelope.get("aggregateType").asString()).isEqualTo("SellerProfile");
            assertThat(envelope.get("aggregateId").asString()).isEqualTo(ownerA.toString());
            assertThat(envelope.get("eventType").asString()).isEqualTo("SellerProfileSubmitted");

            // Payload nghiệp vụ bên trong khung
            JsonNode businessPayload = envelope.get("payload");
            assertThat(businessPayload.isObject()).isTrue();

            // Đúng ba trường: accountId, profileId, submittedAt
            List<String> fieldNames = new ArrayList<>(businessPayload.propertyNames());
            assertThat(fieldNames).containsExactly("accountId", "profileId", "submittedAt");

            assertThat(businessPayload.get("accountId").asString()).isEqualTo(ownerA.toString());
            assertThat(businessPayload.get("profileId").asString()).isEqualTo(createdProfileId.toString());
            assertThat(businessPayload.get("submittedAt").asString()).isNotEmpty();

            // Toàn bộ chuỗi payload không chứa số căn cước, tên pháp lý, địa chỉ kinh doanh, tên gian hàng
            assertThat(rawPayload).doesNotContain(identityNumber);
            assertThat(rawPayload).doesNotContain("Nguyễn Văn An");
            assertThat(rawPayload).doesNotContain("12 Nguyễn Huệ, Quận 1, TP.HCM");
            assertThat(rawPayload).doesNotContain(shopName);
        });
    }

    @Test
    @DisplayName("Nộp lại hồ sơ sau khi bị từ chối ghi hàng outbox thứ hai")
    void resubmitWritesSecondRow() {
        UUID ownerA = UUID.randomUUID();
        String identityNumber = "079123456789";
        SubmitSellerProfile.Command cmd = submitCommand(user(ownerA), identityNumber, "Gian Hàng A");
        SubmitSellerProfile.Result submitResult = submitSellerProfile.handle(cmd);
        UUID profileId = submitResult.profile().getId().getValue();

        assertThat(outboxRows()).isEqualTo(1);

        // Dựng sẵn trạng thái REJECTED trực tiếp trong DB
        jdbcTemplate.update(
                "UPDATE seller_profiles SET status='REJECTED', rejection_reason='Giấy tờ không rõ', reviewed_at=now(), reviewed_by=? WHERE owner_account_id=?",
                UUID.randomUUID(), ownerA
        );

        // Nộp lại thành công
        ResubmitSellerProfile.Command resubmitCmd = new ResubmitSellerProfile.Command(
                user(ownerA),
                SellerType.INDIVIDUAL,
                "Nguyễn Văn An",
                null,
                identityNumber,
                null,
                "12 Nguyễn Huệ, Quận 1, TP.HCM",
                "Gian Hàng A Mới",
                "Mô tả mới",
                "Nguyễn Văn An",
                "0912345678",
                null
        );
        resubmitSellerProfile.handle(resubmitCmd);

        // Tổng 2 hàng hộp thư
        assertThat(outboxRows()).isEqualTo(2);

        // Hàng mới nhất có profileId của chính hồ sơ A
        JsonNode lastEnvelope = lastEnvelope();
        JsonNode payload = lastEnvelope.get("payload");
        assertThat(payload.get("profileId").asString()).isEqualTo(profileId.toString());
        assertThat(payload.get("accountId").asString()).isEqualTo(ownerA.toString());

        // Kiểm tra trạng thái hồ sơ trong bảng seller_profiles
        jdbcTemplate.query("SELECT status, rejection_reason FROM seller_profiles WHERE owner_account_id = ?", rs -> {
            assertThat(rs.getString("status")).isEqualTo(SellerProfileStatus.PENDING_REVIEW.name());
            assertThat(rs.getString("rejection_reason")).isNull();
        }, ownerA);
    }

    @Test
    @DisplayName("Sửa thông tin gian hàng, pháp lý và đọc hồ sơ không ghi thêm hàng hộp thư")
    void editsAndReadsWriteNoOutboxRow() {
        UUID ownerA = UUID.randomUUID();
        SubmitSellerProfile.Command cmd = submitCommand(user(ownerA), "079123456789", "Shop A");
        submitSellerProfile.handle(cmd);

        assertThat(outboxRows()).isEqualTo(1);

        // UpdateShopInfo đổi mô tả
        updateShopInfo.handle(new UpdateShopInfo.Command(
                user(ownerA),
                Patch.absent(),
                Patch.of("Mô tả cập nhật"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        ));
        assertThat(outboxRows()).isEqualTo(1);

        // UpdateLegalInfo đổi tên pháp lý
        updateLegalInfo.handle(new UpdateLegalInfo.Command(
                user(ownerA),
                Patch.absent(),
                Patch.of("Nguyễn Văn An Updated"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        ));
        assertThat(outboxRows()).isEqualTo(1);

        // GetMySellerProfile
        getMySellerProfile.handle(new GetMySellerProfile.Query(ownerA));
        assertThat(outboxRows()).isEqualTo(1);

        // Sửa không đổi gì
        updateShopInfo.handle(new UpdateShopInfo.Command(
                user(ownerA),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        ));
        assertThat(outboxRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("Nộp trùng rollback toàn bộ kể cả hàng outbox")
    void duplicateSubmitRollsBackEverythingIncludingOutboxRow() {
        UUID ownerA = UUID.randomUUID();
        submitSellerProfile.handle(submitCommand(user(ownerA), "079111111111", "Shop Owner A"));
        assertThat(outboxRows()).isEqualTo(1);

        // A nộp lần hai bị SELLER_PROFILE_EXISTS
        assertThatThrownBy(() -> submitSellerProfile.handle(submitCommand(user(ownerA), "079222222222", "Shop Owner A2")))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_PROFILE_EXISTS.code()));

        Integer profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        assertThat(profileCount).isEqualTo(1);
        assertThat(outboxRows()).isEqualTo(1);

        // B nộp trùng tên của A bị SHOP_NAME_TAKEN
        UUID ownerB = UUID.randomUUID();
        assertThatThrownBy(() -> submitSellerProfile.handle(submitCommand(user(ownerB), "079333333333", "Shop Owner A")))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SHOP_NAME_TAKEN.code()));

        profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        assertThat(profileCount).isEqualTo(1);
        assertThat(outboxRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("ADMIN nộp hoặc sửa hồ sơ bị từ chối SELLER_ROLE_NOT_ALLOWED và không thay đổi dữ liệu")
    void adminSubmitCreatesNothing() {
        UUID adminId = UUID.randomUUID();
        assertThatThrownBy(() -> submitSellerProfile.handle(submitCommand(admin(adminId), "079111111111", "Admin Shop")))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED.code()));

        Integer profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        assertThat(profileCount).isZero();
        assertThat(outboxRows()).isZero();

        // Tạo hồ sơ cho A
        UUID ownerA = UUID.randomUUID();
        submitSellerProfile.handle(submitCommand(user(ownerA), "079123456789", "Shop A"));

        // UpdateShopInfo với admin(A) cùng mã tài khoản nhưng vai ADMIN
        assertThatThrownBy(() -> updateShopInfo.handle(new UpdateShopInfo.Command(
                admin(ownerA),
                Patch.of("New Shop Name"),
                Patch.absent(),
                Patch.absent(),
                Patch.absent(),
                Patch.absent()
        ))).isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode().code())
                        .isEqualTo(SellerErrorCode.SELLER_ROLE_NOT_ALLOWED.code()));

        // Hồ sơ không đổi
        SellerProfile current = repository.findByOwner(ownerA).orElseThrow();
        assertThat(current.shopInfo().shopName().display()).isEqualTo("Shop A");
    }

    @Test
    @DisplayName("20 luồng cùng nộp cho một chủ thì chỉ đúng 1 hồ sơ và 1 hàng outbox được tạo (KT-07)")
    void twentyRacingSubmitsOfSameOwnerLeaveExactlyOneProfileAndOneOutboxRow() throws Exception {
        int threads = 20;
        UUID owner = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                String identityNumber = String.format("079%09d", idx);
                String shopName = "Gian Hàng Đua " + idx;

                futures.add(executor.submit(() -> {
                    latch.await();
                    try {
                        submitSellerProfile.handle(submitCommand(user(owner), identityNumber, shopName));
                        return true;
                    } catch (DomainException de) {
                        assertThat(de.getErrorCode().code()).isEqualTo(SellerErrorCode.SELLER_PROFILE_EXISTS.code());
                        return false;
                    } catch (DataIntegrityViolationException dive) {
                        Throwable root = NestedExceptionUtils.getMostSpecificCause(dive);
                        assertThat(root.getMessage()).contains("uq_seller_profiles_owner");
                        return false;
                    }
                }));
            }

            latch.countDown();

            int successes = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(60, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(1);

            Integer profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
            assertThat(profileCount).isEqualTo(1);

            assertThat(outboxRows()).isEqualTo(1);

            // profileId trong hàng outbox là mã hồ sơ duy nhất còn trong bảng
            String profileIdInDb = jdbcTemplate.queryForObject("SELECT id FROM seller_profiles", String.class);
            JsonNode lastEnvelope = lastEnvelope();
            String profileIdInOutbox = lastEnvelope.get("payload").get("profileId").asString();
            assertThat(profileIdInOutbox).isEqualTo(profileIdInDb);

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("20 luồng khác chủ cùng tên gian hàng thì chỉ đúng 1 thành công và 1 hàng outbox")
    void twentyRacingSubmitsOfSameShopNameLeaveExactlyOneRow() throws Exception {
        int threads = 20;
        String sameShopName = "Gian Hàng Duy Nhất";
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                UUID owner = UUID.randomUUID();
                String identityNumber = String.format("079%09d", idx);

                futures.add(executor.submit(() -> {
                    latch.await();
                    try {
                        submitSellerProfile.handle(submitCommand(user(owner), identityNumber, sameShopName));
                        return true;
                    } catch (DomainException de) {
                        assertThat(de.getErrorCode().code()).isEqualTo(SellerErrorCode.SHOP_NAME_TAKEN.code());
                        return false;
                    } catch (DataIntegrityViolationException dive) {
                        Throwable root = NestedExceptionUtils.getMostSpecificCause(dive);
                        assertThat(root.getMessage()).contains("uq_seller_profiles_shop_name_key");
                        return false;
                    }
                }));
            }

            latch.countDown();

            int successes = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(60, TimeUnit.SECONDS)) {
                    successes++;
                }
            }

            assertThat(successes).isEqualTo(1);

            Integer profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
            assertThat(profileCount).isEqualTo(1);
            assertThat(outboxRows()).isEqualTo(1);

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Ghi hộp thư ngoài giao dịch bị từ chối với IllegalTransactionStateException")
    void outboxWriteOutsideTransactionIsRefused() {
        SellerProfileSubmitted event = new SellerProfileSubmitted(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                SellerProfileId.of(UUID.randomUUID()),
                Instant.now()
        );

        assertThatThrownBy(() -> outboxEventStore.store(event))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxRows()).isZero();
    }
}
