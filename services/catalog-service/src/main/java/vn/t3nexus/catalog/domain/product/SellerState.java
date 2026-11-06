package vn.t3nexus.catalog.domain.product;

import java.util.UUID;

/**
 * Trạng thái người bán được đồng bộ trong catalog-service (INV-CAT-95).
 *
 * @param accountId định danh tài khoản người bán
 * @param status    trạng thái duyệt của người bán
 * @param version   phiên bản sự kiện từ IAM
 */
public record SellerState(UUID accountId, SellerStatus status, long version) {

    /**
     * Kiểm tra người bán đã được duyệt hay chưa.
     *
     * @return true nếu người bán ở trạng thái APPROVED
     */
    public boolean isApproved() {
        return status == SellerStatus.APPROVED;
    }
}
