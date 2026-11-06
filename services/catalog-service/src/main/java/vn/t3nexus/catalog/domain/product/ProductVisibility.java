package vn.t3nexus.catalog.domain.product;

/**
 * Lớp tiện ích xác định tính hiển thị công khai của sản phẩm (INV-CAT-95).
 * <p>
 * Đây là định nghĩa duy nhất về điều kiện hiển thị công khai của sản phẩm trong miền catalog:
 * sản phẩm phải đang ở trạng thái PUBLISHED và người bán tương ứng phải tồn tại và đã được duyệt (APPROVED).
 * Các cờ chặn bổ sung (nếu có) sẽ được áp dụng thêm ở tầng truy vấn/tìm kiếm (C1b).
 */
public final class ProductVisibility {

    private ProductVisibility() {
    }

    /**
     * Kiểm tra sản phẩm có hiển thị công khai hay không.
     *
     * @param product     sản phẩm cần kiểm tra
     * @param sellerState trạng thái người bán của sản phẩm
     * @return true nếu sản phẩm đã đăng và người bán đã được duyệt, ngược lại false
     */
    public static boolean isPubliclyVisible(Product product, SellerState sellerState) {
        return product != null && product.isPublished() && sellerState != null && sellerState.isApproved();
    }
}
