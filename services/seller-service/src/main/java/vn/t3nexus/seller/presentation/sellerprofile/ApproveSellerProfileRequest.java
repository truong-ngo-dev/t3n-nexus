package vn.t3nexus.seller.presentation.sellerprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Đối tượng yêu cầu phê duyệt hồ sơ người bán của quản trị viên.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApproveSellerProfileRequest(
        Long version
) {
    @Override
    public String toString() {
        return "ApproveSellerProfileRequest[***]";
    }
}
