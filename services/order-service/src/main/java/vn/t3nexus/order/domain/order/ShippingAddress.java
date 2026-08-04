package vn.t3nexus.order.domain.order;

/**
 * Value Object — snapshot địa chỉ giao hàng tại thời điểm đặt hàng, dù buyer gõ tay hay chọn
 * từ sổ địa chỉ của customer-service. Bất biến sau khi tạo — customer sửa/xoá địa chỉ trong sổ
 * sau đó không ảnh hưởng đơn đã đặt. 2 cấp hành chính (ward, province) theo mô hình sau sáp nhập
 * 01/07/2025 — không còn quận/huyện.
 */
public record ShippingAddress(
        String recipientName,
        String phone,
        String addressLine,
        String ward,
        String province,
        String note
) {
}
