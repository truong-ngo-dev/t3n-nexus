package vn.t3nexus.seller.presentation.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;
import vn.t3nexus.seller.SellerServiceApplication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp chống rò rỉ dữ liệu cá nhân, thông tin định danh và lý do từ chối ở API quản trị (KT-08).
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks"
})
@ExtendWith(OutputCaptureExtension.class)
class AdminSellerProfileLeakIntegrationTest extends SellerProfileApiTestBase {

    @Test
    @DisplayName("noIdentityDataOrRejectionReasonInLogsAcrossAdminOperations: không có thông tin định danh hay lý do từ chối trong log ở các thao tác quản trị")
    void noIdentityDataOrRejectionReasonInLogsAcrossAdminOperations(CapturedOutput output) throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();

        String secretId = "079333444555";
        String secretTax = "0312345678";
        String secretBizAddr = "456 Phố Kín Cổng, Quận Hoàn Kiếm, Hà Nội";
        String secretLegalName = "Hoàng Văn Bí Mật";
        String secretRejectionReason = "Lý do từ chối cực kỳ nhạy cảm và bảo mật";

        String[] secrets = new String[]{
                secretId, secretTax, secretBizAddr, secretLegalName, secretRejectionReason
        };

        // 1. Tạo hồ sơ người bán cá nhân
        String submitBody = """
                {
                  "sellerType": "INDIVIDUAL",
                  "legalInfo": {
                    "legalName": "%s",
                    "identityNumber": "%s",
                    "taxCode": "%s",
                    "businessAddress": "%s"
                  },
                  "shop": {
                    "shopName": "Shop Bí Mật Quản Trị",
                    "description": "Mô tả cửa hàng",
                    "contact": {
                      "personName": "Hoàng Văn Bí Mật",
                      "phone": "0912345678"
                    }
                  }
                }
                """.formatted(secretLegalName, secretId, secretTax, secretBizAddr);

        mockMvc.perform(post("/api/seller/profile")
                        .with(user(sellerA))
                        .contentType(APPLICATION_JSON)
                        .content(submitBody))
                .andExpect(status().isCreated());

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);
        Long currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?", Long.class, profileId);

        // 2. Quản trị viên xem danh sách chờ duyệt
        mockMvc.perform(get("/api/admin/seller-profiles")
                        .with(admin(adminId)))
                .andExpect(status().isOk());

        // 3. Quản trị viên xem chi tiết hồ sơ
        mockMvc.perform(get("/api/admin/seller-profiles/" + profileId)
                        .with(admin(adminId)))
                .andExpect(status().isOk());

        // 4. Quản trị viên từ chối hồ sơ kèm lý do từ chối
        MvcResult rejectResult = mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + currentVersion + ", \"reason\": \"" + secretRejectionReason + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // 5. Kiểm tra log:
        // - Chứa mã log SELLER_PROFILE_REJECTED
        // - Chứa profileId, adminId (reviewedBy)
        // - TUYỆT ĐỐI KHÔNG chứa thông tin định danh hay lý do từ chối (secretRejectionReason)
        String logs = output.getAll();
        assertThat(logs).contains("SELLER_PROFILE_REJECTED", profileId.toString());

        for (String secret : secrets) {
            assertThat(logs)
                    .as("Log không được chứa dữ liệu nhạy cảm: " + secret)
                    .doesNotContain(secret);
        }
    }

    @Test
    @DisplayName("approvedOperationLogsEventCodeWithoutIdentityFields: phê duyệt hồ sơ log đúng SELLER_PROFILE_APPROVED và không rò rỉ dữ liệu")
    void approvedOperationLogsEventCodeWithoutIdentityFields(CapturedOutput output) throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        String secretId = "079888777666";

        submit(sellerB, secretId, "Shop Duyệt Log");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerB);
        Long currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?", Long.class, profileId);

        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + currentVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        String logs = output.getAll();
        assertThat(logs).contains("SELLER_PROFILE_APPROVED", profileId.toString());
        assertThat(logs).doesNotContain(secretId);

        // Kiểm tra bộ đếm seller.profile.decision
        double approvedCount = meterRegistry.find("seller.profile.decision")
                .tags("result", "approved")
                .counter() != null ? meterRegistry.find("seller.profile.decision").tags("result", "approved").counter().count() : 0.0;
        assertThat(approvedCount).isGreaterThanOrEqualTo(1.0);
    }
}
