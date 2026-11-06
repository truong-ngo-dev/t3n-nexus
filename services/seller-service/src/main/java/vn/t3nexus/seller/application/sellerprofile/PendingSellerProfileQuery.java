package vn.t3nexus.seller.application.sellerprofile;

import vn.t3nexus.seller.domain.sellerprofile.SellerType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cổng đọc danh sách hồ sơ người bán chờ duyệt, chỉ chứa thông tin cơ bản, không rò rỉ dữ liệu định danh, ưu tiên hồ sơ cũ nhất trước.
 */
public interface PendingSellerProfileQuery {

    Result find(int page, int size);

    record Item(UUID id, SellerType sellerType, String shopName, Instant submittedAt) {}

    record Result(List<Item> items, int page, int size, long totalElements) {}
}
