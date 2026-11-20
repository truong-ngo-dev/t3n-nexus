package vn.t3nexus.seller.presentation.sellerprofile;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.lib.observability.http.BodySanitizer;
import vn.t3nexus.lib.observability.http.HttpLoggingProperties;
import vn.t3nexus.seller.domain.sellerprofile.Contact;
import vn.t3nexus.seller.domain.sellerprofile.LegalInfo;
import vn.t3nexus.seller.domain.sellerprofile.SellerActor;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfile;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileId;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileRepository;
import vn.t3nexus.seller.domain.sellerprofile.SellerType;
import vn.t3nexus.seller.domain.sellerprofile.ShopInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.seller.SellerServiceApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp chống rò rỉ dữ liệu cá nhân (KT-12), định dạng log và bộ đếm.
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks"
})
@ExtendWith(OutputCaptureExtension.class)
class SellerProfileLeakIntegrationTest extends SellerProfileApiTestBase {

    @Test
    @DisplayName("noPersonalDataInLogsAcrossAllScenarios: không có dữ liệu cá nhân nào xuất hiện trong log hoặc body lỗi")
    void noPersonalDataInLogsAcrossAllScenarios(CapturedOutput output) throws Exception {
        String secretId = "079555123456";
        String secretTax = "0398765432";
        String secretBizAddr = "Số 77 Đường Bí Mật, Quận 7, TP.HCM";
        String secretLegalName = "Trần Bí Mật Duy";
        String secretContactAddr = "Số 88 Ngõ Kín, Quận 8, TP.HCM";
        String secretPhone = "0911222333";

        String[] secrets = new String[]{
                secretId, secretTax, secretBizAddr, secretLegalName, secretContactAddr, secretPhone
        };

        // 1. A nộp thành công (hộ kinh doanh có mã số thuế)
        UUID accountA = UUID.randomUUID();
        String validHouseholdBody = """
                {
                  "sellerType": "HOUSEHOLD",
                  "legalInfo": {
                    "legalName": "%s",
                    "identityNumber": "%s",
                    "taxCode": "%s",
                    "businessAddress": "%s"
                  },
                  "shop": {
                    "shopName": "Shop Bí Mật",
                    "description": "Gian hàng bí mật",
                    "contact": {
                      "personName": "Người Liên Hệ Bí Mật",
                      "phone": "%s",
                      "address": "%s"
                    }
                  }
                }
                """.formatted(secretLegalName, secretId, secretTax, secretBizAddr, secretPhone, secretContactAddr);

        mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(validHouseholdBody))
                .andExpect(status().isCreated());

        // 2. B nộp trùng khóa (409)
        UUID accountB = UUID.randomUUID();
        MvcResult dupResult = mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content(validHouseholdBody))
                .andExpect(status().isConflict())
                .andReturn();
        for (String secret : secrets) {
            assertThat(dupResult.getResponse().getContentAsString()).doesNotContain(secret);
        }

        // 3. Thân sai (số căn cước ngắn, kèm các giá trị đặc trưng khác) (400)
        UUID accountC = UUID.randomUUID();
        String badBody = """
                {
                  "sellerType": "HOUSEHOLD",
                  "legalInfo": {
                    "legalName": "%s",
                    "identityNumber": "123",
                    "taxCode": "%s",
                    "businessAddress": "%s"
                  },
                  "shop": {
                    "shopName": "Shop C",
                    "contact": {
                      "phone": "%s",
                      "address": "%s"
                    }
                  }
                }
                """.formatted(secretLegalName, secretTax, secretBizAddr, secretPhone, secretContactAddr);

        MvcResult badResult = mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountC))
                        .contentType(APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isBadRequest())
                .andReturn();
        for (String secret : secrets) {
            assertThat(badResult.getResponse().getContentAsString()).doesNotContain(secret);
        }

        // 4. PATCH legal-info thành công và PATCH sai kiểu (400)
        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"taxCode\": \"0311112222\"}}"))
                .andExpect(status().isOk());

        MvcResult patchBadResult = mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"identityNumber\": 123}}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        for (String secret : secrets) {
            assertThat(patchBadResult.getResponse().getContentAsString()).doesNotContain(secret);
        }

        // 5. GET của A
        mockMvc.perform(get("/api/seller/profile").with(user(accountA)))
                .andExpect(status().isOk());

        // 6. 10 luồng cùng nộp với CÙNG số căn cước đặc trưng từ 10 tài khoản khác nhau
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            CountDownLatch ready = new CountDownLatch(10);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                final int idx = i;
                UUID acc = UUID.randomUUID();
                String body = """
                        {
                          "sellerType": "INDIVIDUAL",
                          "legalInfo": {
                            "legalName": "%s",
                            "identityNumber": "%s",
                            "businessAddress": "%s"
                          },
                          "shop": {
                            "shopName": "Shop Parallel %d",
                            "contact": {
                              "phone": "%s",
                              "address": "%s"
                            }
                          }
                        }
                        """.formatted(secretLegalName, secretId, secretBizAddr, idx, secretPhone, secretContactAddr);

                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        MvcResult r = mockMvc.perform(post("/api/seller/profile")
                                        .with(user(acc))
                                        .contentType(APPLICATION_JSON)
                                        .content(body))
                                .andReturn();
                        if (r.getResponse().getStatus() >= 400) {
                            for (String secret : secrets) {
                                assertThat(r.getResponse().getContentAsString()).doesNotContain(secret);
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Khẳng định toàn bộ log output không chứa bất kỳ giá trị đặc trưng nào
        String allLogs = output.getAll();
        for (String secret : secrets) {
            assertThat(allLogs).as("Log must not contain: " + secret).doesNotContain(secret);
        }
    }

    @Test
    @DisplayName("logsCarryEventCodes: log mang đầy đủ event codes SELLER_PROFILE_SUBMITTED, SELLER_PROFILE_UPDATED, SELLER_CONFLICT")
    void logsCarryEventCodes(CapturedOutput output) throws Exception {
        UUID accountA = UUID.randomUUID();

        // Nộp thành công
        MvcResult submitResult = mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Event Code Shop")))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = json(submitResult).get("data");
        String profileId = data.get("id").asString();

        assertThat(output.getAll()).contains("SELLER_PROFILE_SUBMITTED", profileId);

        // Sửa có đổi
        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"New Description\"}"))
                .andExpect(status().isOk());

        assertThat(output.getAll()).contains("SELLER_PROFILE_UPDATED", "part=shop");

        // Sửa KHÔNG đổi gì (PATCH {})
        int countBefore = countOccurrences(output.getAll(), "SELLER_PROFILE_UPDATED");
        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        int countAfter = countOccurrences(output.getAll(), "SELLER_PROFILE_UPDATED");
        assertThat(countAfter).isEqualTo(countBefore);

        // Trùng tên gian hàng
        UUID accountB = UUID.randomUUID();
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079999888777", "Event Code Shop")))
                .andExpect(status().isConflict());

        assertThat(output.getAll()).contains("SELLER_CONFLICT", "SHOP_NAME_TAKEN");
    }

    @Test
    @DisplayName("metricsCountSubmissionsAndConflicts: kiểm tra bộ đếm submit và conflict tăng đúng")
    void metricsCountSubmissionsAndConflicts() throws Exception {
        double subFirstBefore = getCounter("seller.profile.submitted", "kind", "first");
        double subResubmitBefore = getCounter("seller.profile.submitted", "kind", "resubmit");
        double conflictShopBefore = getCounter("seller.profile.conflict", "code", "SHOP_NAME_TAKEN");

        // Nộp lần đầu
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Metrics Shop");

        assertThat(getCounter("seller.profile.submitted", "kind", "first")).isEqualTo(subFirstBefore + 1.0);

        // Cập nhật trạng thái sang REJECTED rồi nộp lại
        UUID reviewer = UUID.randomUUID();
        jdbcTemplate.update("UPDATE seller_profiles SET status='REJECTED', rejection_reason='Sai giấy tờ', reviewed_at=now(), reviewed_by=? WHERE owner_account_id=?",
                reviewer, accountA);

        mockMvc.perform(post("/api/seller/profile/resubmit")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Metrics Shop New")))
                .andExpect(status().isOk());

        assertThat(getCounter("seller.profile.submitted", "kind", "resubmit")).isEqualTo(subResubmitBefore + 1.0);

        // Trùng tên gian hàng
        UUID accountB = UUID.randomUUID();
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079888777666", "Metrics Shop New")))
                .andExpect(status().isConflict());

        assertThat(getCounter("seller.profile.conflict", "code", "SHOP_NAME_TAKEN")).isEqualTo(conflictShopBefore + 1.0);

        // PATCH không đổi gì không làm tăng bộ đếm seller.profile.submitted
        double subFirstAfterConflict = getCounter("seller.profile.submitted", "kind", "first");
        double subResubmitAfterConflict = getCounter("seller.profile.submitted", "kind", "resubmit");

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        assertThat(getCounter("seller.profile.submitted", "kind", "first")).isEqualTo(subFirstAfterConflict);
        assertThat(getCounter("seller.profile.submitted", "kind", "resubmit")).isEqualTo(subResubmitAfterConflict);
    }

    @Test
    @DisplayName("requestLogDoesNotContainBodyValuesOnClientError: bộ lọc log không in giá trị thân yêu cầu khi gặp lỗi 400")
    void requestLogDoesNotContainBodyValuesOnClientError(CapturedOutput output) throws Exception {
        UUID account = UUID.randomUUID();
        String secretId = "079888999000";
        String secretName = "Vũ Đình Tuyệt Mật";
        String secretAddr = "99 Phố Kín Cổng Cao Tường, Hà Nội";

        // Gửi POST thiếu khối shop -> 400
        String missingShopBody = """
                {
                  "sellerType": "INDIVIDUAL",
                  "legalInfo": {
                    "legalName": "%s",
                    "identityNumber": "%s",
                    "businessAddress": "%s"
                  }
                }
                """.formatted(secretName, secretId, secretAddr);

        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content(missingShopBody))
                .andExpect(status().isBadRequest());

        String allLogs = output.getAll();
        assertThat(allLogs).doesNotContain(secretId);
        assertThat(allLogs).doesNotContain(secretName);
        assertThat(allLogs).doesNotContain(secretAddr);
    }

    @Test
    @DisplayName("Cấu hình che nội dung của bộ lọc log che mọi trường định danh của người bán")
    void sanitizerConfigMasksSellerIdentityFields() {
        // Bộ lọc log ghi nội dung yêu cầu thành trường có cấu trúc (không hiện trong văn bản console), nên kiểm thẳng bộ làm sạch với cấu hình thật
        HttpLoggingProperties properties = context.getBean(HttpLoggingProperties.class);
        BodySanitizer sanitizer = new BodySanitizer(properties.sanitizerOptions());
        String body = """
                {"sellerType":"BUSINESS","legalInfo":{"legalName":"Công Ty Bí Mật","representativeName":"Lê Đại Diện Kín",
                "identityNumber":"079444555666","taxCode":"0377788899","businessAddress":"Số 5 Phố Ẩn Danh, Hà Nội"},
                "shop":{"shopName":"Cửa hàng công khai","contact":{"personName":"Người Liên Hệ Kín","phone":"0933444555","address":"Số 6 Ngõ Ẩn, Hà Nội"}}}
                """;
        String out = sanitizer.sanitize(body.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json", java.util.Set.of());
        assertThat(out).isNotNull();
        assertThat(out).doesNotContain("Công Ty Bí Mật", "Lê Đại Diện Kín", "079444555666", "0377788899",
                "Số 5 Phố Ẩn Danh", "Người Liên Hệ Kín", "0933444555", "Số 6 Ngõ Ẩn");
    }

    @Test
    @DisplayName("Vi phạm ràng buộc ở cơ sở dữ liệu không đưa dòng Detail (chứa giá trị) vào log")
    void databaseViolationDetailNeverReachesLogs(CapturedOutput output) {
        SellerProfileRepository repository = context.getBean(SellerProfileRepository.class);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        String identity = "079777888999";

        tx.executeWithoutResult(status -> repository.save(profileOf(UUID.randomUUID(), identity, "Cửa Hàng Một")));
        // Hai hồ sơ cùng khóa định danh: vi phạm chỉ mục duy nhất một phần, Postgres kèm dòng Detail có số căn cước
        assertThatThrownBy(() -> tx.executeWithoutResult(
                status -> repository.save(profileOf(UUID.randomUUID(), identity, "Cửa Hàng Hai"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(output.getAll()).doesNotContain(identity);
    }

    private static SellerProfile profileOf(UUID owner, String identityNumber, String shopName) {
        return SellerProfile.submit(
                SellerProfileId.of(UUID.randomUUID()),
                new SellerActor(owner, "USER"),
                LegalInfo.of(SellerType.INDIVIDUAL, "Nguyễn Văn An", null, identityNumber, null, "12 Nguyễn Huệ, Quận 1, TP.HCM"),
                ShopInfo.of(shopName, null, Contact.of("Nguyễn Văn An", "0912345678", null)),
                UUID.randomUUID(),
                java.time.Instant.parse("2026-10-26T03:00:00Z"));
    }

    private double getCounter(String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter != null ? counter.count() : 0.0;
    }

    private int countOccurrences(String text, String substring) {
        Pattern pattern = Pattern.compile(Pattern.quote(substring));
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
