package vn.t3nexus.order.domain.order;

/**
 * Value Object — 1 sản phẩm trong order, chốt lại {@code unitPrice} tại thời điểm đặt hàng
 * (không đọc lại giá từ Catalog BC sau này).
 */
public record OrderLineItem(String skuId, int qty, long unitPrice) {
}
