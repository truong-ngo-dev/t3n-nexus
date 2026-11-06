package vn.t3nexus.seller.application.sellerprofile.event;

/**
 * Hằng số định danh cho các hành động kiểm toán hồ sơ người bán.
 */
public final class SellerAuditActions {

    public static final String PROFILE_APPROVED = "SELLER_PROFILE_APPROVED";
    public static final String PROFILE_REJECTED = "SELLER_PROFILE_REJECTED";
    public static final String TARGET_TYPE = "SELLER_PROFILE";
    public static final String ADMIN_ROLE = "ADMIN";

    private SellerAuditActions() {
    }
}
