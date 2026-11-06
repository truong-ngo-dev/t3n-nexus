package vn.t3nexus.catalog.application.product;

import java.util.UUID;

/**
 * Ảnh sản phẩm trả về cho người bán và người xem chi tiết.
 */
public record ImageView(UUID id, String url, int position, String contentType, long size) {
}
