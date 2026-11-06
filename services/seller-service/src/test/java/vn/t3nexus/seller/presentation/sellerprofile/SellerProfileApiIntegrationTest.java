package vn.t3nexus.seller.presentation.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.seller.SellerServiceApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp API hồ sơ người bán (KT-10, KT-11).
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks"
})
class SellerProfileApiIntegrationTest extends SellerProfileApiTestBase {

    // --- KT-11: Danh tính & phân quyền ---

    @Test
    @DisplayName("noTokenIs401ForAllFiveEndpoints: cả 5 điểm cuối khi không có token đều trả 401")
    void noTokenIs401ForAllFiveEndpoints() throws Exception {
        mockMvc.perform(post("/api/seller/profile")
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Shop An")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/seller/profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/seller/profile/resubmit")
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Shop An")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unknownPathsAreDenied: các đường dẫn ngoài /api/seller/** đều bị từ chối 403")
    void unknownPathsAreDenied() throws Exception {
        UUID account = UUID.randomUUID();
        mockMvc.perform(get("/api/other").with(user(account)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/env").with(user(account)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/").with(user(account)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("adminSubmitIs403AndCreatesNothing: admin nộp hồ sơ bị 403 SELLER_ROLE_NOT_ALLOWED và không lưu gì")
    void adminSubmitIs403AndCreatesNothing() throws Exception {
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(post("/api/seller/profile")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Shop Admin")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        Integer profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
        assertThat(profileCount).isZero();
        assertThat(outboxCount).isZero();
    }

    @Test
    @DisplayName("adminPatchAndResubmitAre403: admin gọi patch hoặc resubmit bị 403 và dữ liệu không đổi")
    void adminPatchAndResubmitAre403() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(admin(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Mô tả bởi admin\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(admin(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"legalName\": \"Tên mới\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(post("/api/seller/profile/resubmit")
                        .with(admin(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Shop A mới")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        String desc = jdbcTemplate.queryForObject("SELECT description FROM seller_profiles WHERE owner_account_id = ?",
                String.class, accountA);
        assertThat(desc).isEqualTo("Mô tả gian hàng");
    }

    @Test
    @DisplayName("adminGetMineIs404: admin GET hồ sơ của mình bị 404 SELLER_PROFILE_NOT_FOUND")
    void adminGetMineIs404() throws Exception {
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(get("/api/seller/profile").with(admin(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_NOT_FOUND"));
    }

    // --- KT-10: Thành công & Các luồng nghiệp vụ chuẩn ---

    @Test
    @DisplayName("submitReturns201WithFullProfileAndNoVersion: nộp hồ sơ thành công trả 201, đủ trường và không rò version/khóa nội bộ")
    void submitReturns201WithFullProfileAndNoVersion() throws Exception {
        UUID accountA = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Gian Hàng A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.sellerType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.data.legalInfo.identityNumber").value("079123456789"))
                .andExpect(jsonPath("$.data.legalInfo.businessAddress").value("12 Nguyễn Huệ, Quận 1, TP.HCM"))
                .andExpect(jsonPath("$.data.shop.shopName").value("Gian Hàng A"))
                .andExpect(jsonPath("$.data.shop.contact.phone").value("0912345678"))
                .andReturn();

        JsonNode body = json(result);
        JsonNode data = body.get("data");
        assertThat(data.has("submittedAt")).isTrue();
        assertThat(data.has("version")).isFalse();
        assertThat(data.has("ownerAccountId")).isFalse();
        assertThat(data.has("identityKey")).isFalse();
        assertThat(data.has("reviewedBy")).isFalse();
        assertThat(data.has("reviewedAt")).isFalse();

        UUID ownerInDb = jdbcTemplate.queryForObject("SELECT owner_account_id FROM seller_profiles WHERE owner_account_id = ?",
                UUID.class, accountA);
        assertThat(ownerInDb).isEqualTo(accountA);

        Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("ownerComesFromTokenNotFromBody: ownerAccountId luôn lấy từ token, bỏ qua các trường lạ trong body")
    void ownerComesFromTokenNotFromBody() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID fakeAccountB = UUID.randomUUID();
        UUID fakeProfileId = UUID.randomUUID();

        String bodyWithFakeFields = """
                {
                  "accountId": "%s",
                  "ownerAccountId": "%s",
                  "id": "%s",
                  "sellerType": "INDIVIDUAL",
                  "legalInfo": {
                    "legalName": "Nguyễn Văn An",
                    "identityNumber": "079123456789",
                    "businessAddress": "12 Nguyễn Huệ, Quận 1, TP.HCM"
                  },
                  "shop": {
                    "shopName": "Shop An",
                    "description": "Mô tả gian hàng",
                    "contact": {
                      "personName": "Nguyễn Văn An",
                      "phone": "0912345678"
                    }
                  }
                }
                """.formatted(fakeAccountB, fakeAccountB, fakeProfileId);

        MvcResult result = mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(bodyWithFakeFields))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data = json(result).get("data");
        UUID returnedId = UUID.fromString(data.get("id").asString());
        assertThat(returnedId).isNotEqualTo(fakeProfileId);

        UUID ownerInDb = jdbcTemplate.queryForObject("SELECT owner_account_id FROM seller_profiles WHERE id = ?",
                UUID.class, returnedId);
        assertThat(ownerInDb).isEqualTo(accountA);
    }

    @Test
    @DisplayName("getMineReturnsProfile: lấy hồ sơ của chính mình trả về 200 và không có trường version")
    void getMineReturnsProfile() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        MvcResult result = mockMvc.perform(get("/api/seller/profile").with(user(accountA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shop.shopName").value("Shop A"))
                .andReturn();

        JsonNode data = json(result).get("data");
        assertThat(data.has("version")).isFalse();
    }

    @Test
    @DisplayName("getMineWhenNoneIs404: lấy hồ sơ khi chưa nộp trả 404 SELLER_PROFILE_NOT_FOUND")
    void getMineWhenNoneIs404() throws Exception {
        UUID accountB = UUID.randomUUID();
        mockMvc.perform(get("/api/seller/profile").with(user(accountB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("isolationBetweenTwoAccounts: cô lập giữa hai tài khoản A và B")
    void isolationBetweenTwoAccounts() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        mockMvc.perform(get("/api/seller/profile").with(user(accountB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Hacked\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_NOT_FOUND"));

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"legalName\": \"Hacked\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_NOT_FOUND"));

        String descA = jdbcTemplate.queryForObject("SELECT description FROM seller_profiles WHERE owner_account_id = ?",
                String.class, accountA);
        assertThat(descA).isEqualTo("Mô tả gian hàng");
    }

    @Test
    @DisplayName("patchShopInfoKeepsAbsentAndClearsExplicitNull: PATCH shop-info giữ nguyên trường vắng và xóa trường null")
    void patchShopInfoKeepsAbsentAndClearsExplicitNull() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Mô tả mới\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shop.shopName").value("Shop A"))
                .andExpect(jsonPath("$.data.shop.description").value("Mô tả mới"));

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shop.description").isEmpty());

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"contact\": {\"phone\": \"0987654321\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shop.contact.personName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.data.shop.contact.phone").value("0987654321"));

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"contact\": {\"address\": \"34 Hai Bà Trưng, Quận 3, TP.HCM\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shop.contact.address").value("34 Hai Bà Trưng, Quận 3, TP.HCM"));

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"contact\": {\"address\": null}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shop.contact.address").isEmpty());
    }

    @Test
    @DisplayName("patchShopInfoNoChangeReturnsCurrent: PATCH không thay đổi trả về 200 và version không tăng")
    void patchShopInfoNoChangeReturnsCurrent() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        Long initialVersion = jdbcTemplate.queryForObject("SELECT version FROM seller_profiles WHERE owner_account_id = ?",
                Long.class, accountA);

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"shopName\": \"Shop A\", \"description\": \"Mô tả gian hàng\"}"))
                .andExpect(status().isOk());

        Long afterVersion = jdbcTemplate.queryForObject("SELECT version FROM seller_profiles WHERE owner_account_id = ?",
                Long.class, accountA);
        assertThat(afterVersion).isEqualTo(initialVersion);
    }

    @Test
    @DisplayName("patchLegalInfoMergesAndRecomputes: PATCH legal-info gộp trường và tính lại identityKey")
    void patchLegalInfoMergesAndRecomputes() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"legalName\": \"Nguyễn Văn Bảo\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legalInfo.legalName").value("Nguyễn Văn Bảo"))
                .andExpect(jsonPath("$.data.legalInfo.identityNumber").value("079123456789"));

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"identityNumber\": \"079999999999\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legalInfo.identityNumber").value("079999999999"));

        String identityKeyInDb = jdbcTemplate.queryForObject("SELECT identity_key FROM seller_profiles WHERE owner_account_id = ?",
                String.class, accountA);
        assertThat(identityKeyInDb).isEqualTo("079999999999");
    }

    @Test
    @DisplayName("resubmitFromRejectedReturns200AndClearsReason: nộp lại từ REJECTED thành công, xóa lý do từ chối và ghi thêm outbox")
    void resubmitFromRejectedReturns200AndClearsReason() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        UUID reviewer = UUID.randomUUID();
        jdbcTemplate.update("UPDATE seller_profiles SET status='REJECTED', rejection_reason='Giấy tờ không rõ', reviewed_at=now(), reviewed_by=? WHERE owner_account_id=?",
                reviewer, accountA);

        mockMvc.perform(get("/api/seller/profile").with(user(accountA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rejectionReason").value("Giấy tờ không rõ"));

        mockMvc.perform(post("/api/seller/profile/resubmit")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Shop A Mới")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.rejectionReason").isEmpty())
                .andExpect(jsonPath("$.data.shop.shopName").value("Shop A Mới"));

        Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
        assertThat(outboxCount).isEqualTo(2);
    }

    // --- KT-10: Các ca lỗi ---

    @Test
    @DisplayName("validationErrorsAre400WithFieldNameAndNoValue: lỗi định dạng trả 400 và không rò giá trị đầu vào")
    void validationErrorsAre400WithFieldNameAndNoValue() throws Exception {
        UUID account = UUID.randomUUID();

        // 1. Số căn cước 123
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("123", "Shop Valid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTITY_NUMBER"))
                .andExpect(jsonPath("$.message").value(containsString("identityNumber")))
                .andExpect(jsonPath("$.message").value(not(containsString("123"))));

        // 2. Thiếu khối legalInfo
        String missingLegal = """
                {
                  "sellerType": "INDIVIDUAL",
                  "shop": {
                    "shopName": "Shop Valid",
                    "contact": { "personName": "An", "phone": "0912345678" }
                  }
                }
                """;
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content(missingLegal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEGAL_INFO_REQUIRED"));

        // 3. Tên gian hàng ab (dưới 3 ký tự)
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "ab")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("shopName")));

        // 4. sellerType = COMPANY
        String invalidType = """
                {
                  "sellerType": "COMPANY",
                  "legalInfo": {
                    "legalName": "Nguyễn Văn An",
                    "identityNumber": "079123456789",
                    "businessAddress": "12 Nguyễn Huệ, Quận 1, TP.HCM"
                  },
                  "shop": {
                    "shopName": "Shop Valid",
                    "contact": { "personName": "An", "phone": "0912345678" }
                  }
                }
                """;
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content(invalidType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"));

        // 5. Doanh nghiệp thiếu người đại diện
        String businessNoRep = """
                {
                  "sellerType": "BUSINESS",
                  "legalInfo": {
                    "legalName": "Công ty TNHH",
                    "identityNumber": "079123456789",
                    "businessAddress": "12 Nguyễn Huệ, Quận 1, TP.HCM"
                  },
                  "shop": {
                    "shopName": "Shop Valid",
                    "contact": { "personName": "An", "phone": "0912345678" }
                  }
                }
                """;
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content(businessNoRep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEGAL_INFO_REQUIRED"));

        // 6. Thân hỏng
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("patchBadTypesAre400: PATCH sai kiểu trả 400 SELLER_FIELD_INVALID")
    void patchBadTypesAre400() throws Exception {
        UUID account = UUID.randomUUID();
        submit(account, "079123456789", "Shop A");

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": 123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"));

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content("{\"shopName\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"));

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("conflictsAre409: trùng tài khoản, trùng tên gian hàng, trùng căn cước trả 409")
    void conflictsAre409() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        // A nộp lần 2
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079987654321", "Shop Khác")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_EXISTS"));

        // B nộp trùng tên gian hàng (khác hoa thường)
        UUID accountB = UUID.randomUUID();
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079888888888", "shop a")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_NAME_TAKEN"));

        // B nộp trùng căn cước của A
        mockMvc.perform(post("/api/seller/profile")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Shop B")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTITY_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.message").value(not(containsString("079123456789"))));

        Integer profileCount = jdbcTemplate.queryForObject("SELECT count(*) FROM seller_profiles", Integer.class);
        Integer outboxCount = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
        assertThat(profileCount).isEqualTo(1);
        assertThat(outboxCount).isEqualTo(1);
    }

    @Test
    @DisplayName("stateErrorsAre409: thao tác sai trạng thái trả 409")
    void stateErrorsAre409() throws Exception {
        UUID accountA = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");

        UUID reviewer = UUID.randomUUID();
        // Chuyển sang REJECTED
        jdbcTemplate.update("UPDATE seller_profiles SET status='REJECTED', rejection_reason='Lý do', reviewed_at=now(), reviewed_by=? WHERE owner_account_id=?",
                reviewer, accountA);

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Mới\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"legalName\": \"Mới\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Chuyển sang APPROVED
        jdbcTemplate.update("UPDATE seller_profiles SET status='APPROVED', rejection_reason=null, reviewed_at=now(), reviewed_by=? WHERE owner_account_id=?",
                reviewer, accountA);

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"legalInfo\": {\"legalName\": \"Mới\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEGAL_INFO_LOCKED"));

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Mô tả đã duyệt\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/seller/profile/resubmit")
                        .with(user(accountA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody("079123456789", "Shop A")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("changeToBusinessWithoutRepresentativeIs400: chuyển sang BUSINESS thiếu đại diện trả 400")
    void changeToBusinessWithoutRepresentativeIs400() throws Exception {
        UUID account = UUID.randomUUID();
        submit(account, "079123456789", "Shop A");

        mockMvc.perform(patch("/api/seller/profile/legal-info")
                        .with(user(account))
                        .contentType(APPLICATION_JSON)
                        .content("{\"sellerType\": \"BUSINESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LEGAL_INFO_REQUIRED"));
    }

    @Test
    @DisplayName("shopNameConflictOnPatchIs409: đổi tên gian hàng trùng tên khác khi PATCH trả 409 SHOP_NAME_TAKEN")
    void shopNameConflictOnPatchIs409() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        submit(accountA, "079123456789", "Shop A");
        submit(accountB, "079987654321", "Shop B");

        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(accountB))
                        .contentType(APPLICATION_JSON)
                        .content("{\"shopName\": \"Shop A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHOP_NAME_TAKEN"));
    }

    @Test
    @DisplayName("parallelEditsGiveOneSuccessAndMaybeConcurrentUpdate: chỉnh sửa đồng thời 2 luồng trả về 200 hoặc 409 CONCURRENT_UPDATE")
    void parallelEditsGiveOneSuccessAndMaybeConcurrentUpdate() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                UUID account = UUID.randomUUID();
                submit(account, "07912345678" + (round % 10), "Parallel Shop " + round);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
                List<String> codes = Collections.synchronizedList(new ArrayList<>());

                Runnable task1 = () -> {
                    ready.countDown();
                    try {
                        start.await();
                        MvcResult res = mockMvc.perform(patch("/api/seller/profile/shop-info")
                                        .with(user(account))
                                        .contentType(APPLICATION_JSON)
                                        .content("{\"description\": \"Desc Branch 1\"}"))
                                .andReturn();
                        statuses.add(res.getResponse().getStatus());
                        JsonNode tree = objectMapper.readTree(res.getResponse().getContentAsString());
                        if (tree.has("code") && !tree.get("code").isNull()) {
                            codes.add(tree.get("code").asString());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                Runnable task2 = () -> {
                    ready.countDown();
                    try {
                        start.await();
                        MvcResult res = mockMvc.perform(patch("/api/seller/profile/shop-info")
                                        .with(user(account))
                                        .contentType(APPLICATION_JSON)
                                        .content("{\"description\": \"Desc Branch 2\"}"))
                                .andReturn();
                        statuses.add(res.getResponse().getStatus());
                        JsonNode tree = objectMapper.readTree(res.getResponse().getContentAsString());
                        if (tree.has("code") && !tree.get("code").isNull()) {
                            codes.add(tree.get("code").asString());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                Future<?> f1 = executor.submit(task1);
                Future<?> f2 = executor.submit(task2);

                ready.await(5, TimeUnit.SECONDS);
                start.countDown();

                f1.get(5, TimeUnit.SECONDS);
                f2.get(5, TimeUnit.SECONDS);

                assertThat(statuses).doesNotContain(500);
                assertThat(statuses).contains(200);

                for (int st : statuses) {
                    assertThat(st == 200 || st == 409).isTrue();
                }
                for (String c : codes) {
                    assertThat(c).isEqualTo("CONCURRENT_UPDATE");
                }

                String finalDesc = jdbcTemplate.queryForObject("SELECT description FROM seller_profiles WHERE owner_account_id = ?",
                        String.class, account);
                assertThat(finalDesc).isIn("Desc Branch 1", "Desc Branch 2");
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
