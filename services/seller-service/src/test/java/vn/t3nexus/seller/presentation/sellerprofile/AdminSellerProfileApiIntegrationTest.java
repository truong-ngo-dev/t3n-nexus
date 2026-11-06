package vn.t3nexus.seller.presentation.sellerprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import vn.t3nexus.seller.SellerServiceApplication;

import java.util.UUID;

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
 * Kiểm thử tích hợp API quản trị và bảo mật hồ sơ người bán (KT-06, KT-07).
 */
@SpringBootTest(classes = SellerServiceApplication.class, properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/jwks"
})
class AdminSellerProfileApiIntegrationTest extends SellerProfileApiTestBase {

    // --- KT-07: Xác thực và phân quyền (401, 403) ---

    @Test
    @DisplayName("noTokenReturns401OnAllAdminEndpoints: cả 4 điểm cuối quản trị khi không có token đều trả về 401")
    void noTokenReturns401OnAllAdminEndpoints() throws Exception {
        UUID profileId = UUID.randomUUID();

        // 1. Danh sách chờ duyệt
        mockMvc.perform(get("/api/admin/seller-profiles"))
                .andExpect(status().isUnauthorized());

        // 2. Chi tiết hồ sơ
        mockMvc.perform(get("/api/admin/seller-profiles/" + profileId))
                .andExpect(status().isUnauthorized());

        // 3. Phê duyệt hồ sơ
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1}"))
                .andExpect(status().isUnauthorized());

        // 4. Từ chối hồ sơ
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1, \"reason\": \"Lý do từ chối\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("userOrEmptyRoleReturns403OnAllAdminEndpointsAndDoesNotReadRepo: người dùng vai USER hoặc vai rỗng gọi 4 điểm cuối quản trị đều trả về 403 SELLER_ROLE_NOT_ALLOWED")
    void userOrEmptyRoleReturns403OnAllAdminEndpointsAndDoesNotReadRepo() throws Exception {
        UUID userAccount = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        // Kiểm tra với vai USER
        mockMvc.perform(get("/api/admin/seller-profiles").with(user(userAccount)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(get("/api/admin/seller-profiles/" + profileId).with(user(userAccount)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(user(userAccount))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(user(userAccount))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1, \"reason\": \"Lý do từ chối\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        // Kiểm tra với vai rỗng (token không claim role hoặc role = "")
        var emptyRoleToken = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(userAccount.toString()).claim("role", ""));

        mockMvc.perform(get("/api/admin/seller-profiles").with(emptyRoleToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(get("/api/admin/seller-profiles/" + profileId).with(emptyRoleToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(emptyRoleToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));

        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(emptyRoleToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1, \"reason\": \"Lý do từ chối\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_ROLE_NOT_ALLOWED"));
    }

    // --- KT-07 & KT-06: Danh sách chờ duyệt (GET /api/admin/seller-profiles) ---

    @Test
    @DisplayName("listPendingSellerProfilesSuccessAndStructure: xem danh sách chờ duyệt thành công, chỉ trả đúng 4 khóa cơ bản, không có dữ liệu định danh")
    void listPendingSellerProfilesSuccessAndStructure() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();

        submit(sellerA, "079111222333", "Shop A Cơ Bản");
        submit(sellerB, "079444555666", "Shop B Cơ Bản");

        MvcResult result = mockMvc.perform(get("/api/admin/seller-profiles")
                        .with(admin(adminId))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andReturn();

        JsonNode root = json(result);
        JsonNode firstItem = root.get("data").get("items").get(0);

        // Khẳng định đúng 4 khóa cho mỗi dòng danh sách
        assertThat(firstItem.has("id")).isTrue();
        assertThat(firstItem.has("sellerType")).isTrue();
        assertThat(firstItem.has("shopName")).isTrue();
        assertThat(firstItem.has("submittedAt")).isTrue();

        // Khẳng định KHÔNG chứa các trường định danh và version
        assertThat(firstItem.has("identityNumber")).isFalse();
        assertThat(firstItem.has("taxCode")).isFalse();
        assertThat(firstItem.has("businessAddress")).isFalse();
        assertThat(firstItem.has("legalName")).isFalse();
        assertThat(firstItem.has("representativeName")).isFalse();
        assertThat(firstItem.has("legalInfo")).isFalse();
        assertThat(firstItem.has("version")).isFalse();
        assertThat(firstItem.has("ownerAccountId")).isFalse();
        assertThat(firstItem.has("identityKey")).isFalse();
    }

    @Test
    @DisplayName("listPendingSellerProfilesBadPageOrSizeIs400: tham số page âm hoặc size ngoài 1 đến 50 trả về 400 SELLER_FIELD_INVALID")
    void listPendingSellerProfilesBadPageOrSizeIs400() throws Exception {
        UUID adminId = UUID.randomUUID();

        // page âm
        mockMvc.perform(get("/api/admin/seller-profiles")
                        .with(admin(adminId))
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("page")));

        // size = 0
        mockMvc.perform(get("/api/admin/seller-profiles")
                        .with(admin(adminId))
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("size")));

        // size = 51 (vượt quá 50)
        mockMvc.perform(get("/api/admin/seller-profiles")
                        .with(admin(adminId))
                        .param("page", "0")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("size")));
    }

    // --- KT-07 & KT-06: Chi tiết hồ sơ (GET /api/admin/seller-profiles/{profileId}) ---

    @Test
    @DisplayName("getSellerProfileForReviewReturnsFullDetailsAcrossAllStatuses: xem chi tiết hồ sơ trả đủ trường kèm version ở mọi trạng thái")
    void getSellerProfileForReviewReturnsFullDetailsAcrossAllStatuses() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        submit(sellerA, "079123456789", "Shop Xem Chi Tiết");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);

        // 1. Trạng thái PENDING_REVIEW
        MvcResult pendingResult = mockMvc.perform(get("/api/admin/seller-profiles/" + profileId)
                        .with(admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(profileId.toString()))
                .andExpect(jsonPath("$.data.ownerAccountId").value(sellerA.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.legalInfo.identityNumber").value("079123456789"))
                .andExpect(jsonPath("$.data.legalInfo.businessAddress").value("12 Nguyễn Huệ, Quận 1, TP.HCM"))
                .andExpect(jsonPath("$.data.shop.shopName").value("Shop Xem Chi Tiết"))
                .andReturn();

        JsonNode data = json(pendingResult).get("data");
        assertThat(data.has("reviewedAt")).isTrue();
        assertThat(data.has("reviewedBy")).isTrue();
        assertThat(data.has("rejectionReason")).isTrue();

        // 2. Chuyển sang REJECTED và kiểm tra vẫn trả 200 kèm rejectionReason
        jdbcTemplate.update("UPDATE seller_profiles SET status='REJECTED', rejection_reason='Ảnh mờ', reviewed_at=now(), reviewed_by=?, version=version+1 WHERE id=?",
                adminId, profileId);

        mockMvc.perform(get("/api/admin/seller-profiles/" + profileId)
                        .with(admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Ảnh mờ"))
                .andExpect(jsonPath("$.data.reviewedBy").value(adminId.toString()))
                .andExpect(jsonPath("$.data.version").value(1));

        // 3. Chuyển sang APPROVED và kiểm tra vẫn trả 200
        jdbcTemplate.update("UPDATE seller_profiles SET status='APPROVED', rejection_reason=null, reviewed_at=now(), reviewed_by=?, version=version+1 WHERE id=?",
                adminId, profileId);

        mockMvc.perform(get("/api/admin/seller-profiles/" + profileId)
                        .with(admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.rejectionReason").isEmpty())
                .andExpect(jsonPath("$.data.reviewedBy").value(adminId.toString()))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    @DisplayName("getSellerProfileNotFoundAndMalformedId: mã không tồn tại trả 404, mã không phải UUID trả 400")
    void getSellerProfileNotFoundAndMalformedId() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();

        // 404 mã không tồn tại
        mockMvc.perform(get("/api/admin/seller-profiles/" + nonExistentId)
                        .with(admin(adminId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_NOT_FOUND"));

        // 400 mã không phải UUID
        mockMvc.perform(get("/api/admin/seller-profiles/invalid-uuid-format")
                        .with(admin(adminId)))
                .andExpect(status().isBadRequest());
    }

    // --- KT-07: Phê duyệt (POST /api/admin/seller-profiles/{profileId}/approve) ---

    @Test
    @DisplayName("approveSellerProfileSuccess: duyệt hồ sơ thành công trả 200, status=APPROVED, reviewedBy=adminId, version tăng 1")
    void approveSellerProfileSuccess() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        submit(sellerA, "079123456789", "Shop Cho Phép Duyệt");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);
        Long currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?", Long.class, profileId);

        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + currentVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(profileId.toString()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedBy").value(adminId.toString()))
                .andExpect(jsonPath("$.data.version").value(currentVersion + 1));

        String statusInDb = jdbcTemplate.queryForObject(
                "SELECT status FROM seller_profiles WHERE id = ?", String.class, profileId);
        assertThat(statusInDb).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("approveValidationErrors: thiếu version trả 400, mã không phải UUID trả 400, không tồn tại trả 404")
    void approveValidationErrors() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        submit(sellerA, "079123456789", "Shop Kiểm Tra Lỗi Duyệt");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);

        // Thiếu version trong body
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("version")));

        // Mã không phải UUID
        mockMvc.perform(post("/api/admin/seller-profiles/not-a-uuid/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1}"))
                .andExpect(status().isBadRequest());

        // Mã không tồn tại
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/seller-profiles/" + nonExistentId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_PROFILE_NOT_FOUND"));
    }

    // --- KT-07: Từ chối (POST /api/admin/seller-profiles/{profileId}/reject) ---

    @Test
    @DisplayName("rejectSellerProfileSuccess: từ chối hồ sơ thành công trả 200, status=REJECTED, có rejectionReason, reviewedBy=adminId")
    void rejectSellerProfileSuccess() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        submit(sellerA, "079123456789", "Shop Cho Phép Từ Chối");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);
        Long currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?", Long.class, profileId);

        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + currentVersion + ", \"reason\": \"  Giấy tờ kinh doanh bị mờ  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(profileId.toString()))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Giấy tờ kinh doanh bị mờ"))
                .andExpect(jsonPath("$.data.reviewedBy").value(adminId.toString()))
                .andExpect(jsonPath("$.data.version").value(currentVersion + 1));

        String reasonInDb = jdbcTemplate.queryForObject(
                "SELECT rejection_reason FROM seller_profiles WHERE id = ?", String.class, profileId);
        assertThat(reasonInDb).isEqualTo("Giấy tờ kinh doanh bị mờ");
    }

    @Test
    @DisplayName("rejectValidationErrors: thiếu reason hoặc khoảng trắng trả 400 REJECTION_REASON_REQUIRED, quá 500 ký tự trả 400 SELLER_FIELD_INVALID")
    void rejectValidationErrors() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        submit(sellerA, "079123456789", "Shop Kiểm Tra Lý Do");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);

        // 1. Thiếu reason
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));

        // 2. Reason toàn khoảng trắng
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1, \"reason\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));

        // 3. Reason quá 500 ký tự
        String longReason = "a".repeat(501);
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": 1, \"reason\": \"" + longReason + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("reason")))
                .andExpect(jsonPath("$.message").value(not(containsString(longReason))));

        // 4. Thiếu version
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\": \"Lý do hợp lệ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELLER_FIELD_INVALID"))
                .andExpect(jsonPath("$.message").value(containsString("version")));
    }

    // --- KT-07: Xung đột trạng thái và phiên bản (409 INVALID_STATE, STALE_VERSION) ---

    @Test
    @DisplayName("decisionConflictsAre409WithCorrectCodes: sai trạng thái trả INVALID_STATE, lệch phiên bản trả STALE_VERSION")
    void decisionConflictsAre409WithCorrectCodes() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        submit(sellerA, "079123456789", "Shop Xung Đột");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);
        Long initialVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?", Long.class, profileId);

        // 1. STALE_VERSION: gửi version khác (ví dụ: initialVersion + 99)
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + (initialVersion + 99) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        // 2. Chuyển trạng thái sang APPROVED rồi thử duyệt/từ chối lại -> INVALID_STATE
        jdbcTemplate.update("UPDATE seller_profiles SET status='APPROVED', reviewed_at=now(), reviewed_by=?, version=version+1 WHERE id=?",
                adminId, profileId);
        Long approvedVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM seller_profiles WHERE id = ?", Long.class, profileId);

        // Duyệt hồ sơ đã APPROVED (kể cả đúng phiên bản hiện tại)
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + approvedVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Từ chối hồ sơ đã APPROVED
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/reject")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + approvedVersion + ", \"reason\": \"Muốn từ chối\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        // Thứ tự kiểm: hồ sơ đã APPROVED nhưng gửi phiên bản cũ -> vẫn nhận INVALID_STATE (không nhận STALE_VERSION)
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + initialVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("staleVersionWhenSellerEditedAfterAdminViewed: chủ hồ sơ sửa sau khi quản trị xem chi tiết, duyệt phiên bản cũ nhận 409 STALE_VERSION")
    void staleVersionWhenSellerEditedAfterAdminViewed() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID sellerA = UUID.randomUUID();
        submit(sellerA, "079123456789", "Shop Đua Sửa");

        UUID profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM seller_profiles WHERE owner_account_id = ?", UUID.class, sellerA);

        // Bước 1: 9:00 Quản trị mở xem chi tiết, ghi nhận version = 1
        MvcResult viewResult = mockMvc.perform(get("/api/admin/seller-profiles/" + profileId)
                        .with(admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();
        long viewedVersion = json(viewResult).get("data").get("version").asLong();

        // Bước 2: 9:02 Chủ hồ sơ sửa thông tin gian hàng (version tăng lên 2)
        mockMvc.perform(patch("/api/seller/profile/shop-info")
                        .with(user(sellerA))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\": \"Chủ hồ sơ đã cập nhật mô tả mới\"}"))
                .andExpect(status().isOk());

        // Bước 3: 9:05 Quản trị bấm duyệt kèm phiên bản đã xem (viewedVersion = 1) -> nhận 409 STALE_VERSION
        mockMvc.perform(post("/api/admin/seller-profiles/" + profileId + "/approve")
                        .with(admin(adminId))
                        .contentType(APPLICATION_JSON)
                        .content("{\"version\": " + viewedVersion + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        // Hồ sơ vẫn giữ nguyên trạng thái PENDING_REVIEW
        String statusInDb = jdbcTemplate.queryForObject(
                "SELECT status FROM seller_profiles WHERE id = ?", String.class, profileId);
        assertThat(statusInDb).isEqualTo("PENDING_REVIEW");
    }
}
