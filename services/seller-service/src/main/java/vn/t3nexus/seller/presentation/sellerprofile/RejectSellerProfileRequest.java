package vn.t3nexus.seller.presentation.sellerprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Đối tượng yêu cầu từ chối hồ sơ người bán của quản trị viên.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RejectSellerProfileRequest(
        Long version,
        String reason
) {
    @Override
    public String toString() {
        return "RejectSellerProfileRequest[***]";
    }
}
