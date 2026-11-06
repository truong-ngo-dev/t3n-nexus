package vn.t3nexus.seller.presentation.sellerprofile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import vn.t3nexus.seller.application.sellerprofile.PendingSellerProfileQuery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Đối tượng phản hồi trang danh sách hồ sơ người bán chờ duyệt.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingSellerProfilePageResponse(
        List<PendingSellerProfileItemResponse> items,
        int page,
        int size,
        long totalElements
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PendingSellerProfileItemResponse(
            UUID id,
            String sellerType,
            String shopName,
            Instant submittedAt
    ) {
        public static PendingSellerProfileItemResponse from(PendingSellerProfileQuery.Item item) {
            return new PendingSellerProfileItemResponse(
                    item.id(),
                    item.sellerType().name(),
                    item.shopName(),
                    item.submittedAt()
            );
        }
    }

    public static PendingSellerProfilePageResponse from(PendingSellerProfileQuery.Result result) {
        List<PendingSellerProfileItemResponse> items = result.items().stream()
                .map(PendingSellerProfileItemResponse::from)
                .toList();
        return new PendingSellerProfilePageResponse(items, result.page(), result.size(), result.totalElements());
    }
}
