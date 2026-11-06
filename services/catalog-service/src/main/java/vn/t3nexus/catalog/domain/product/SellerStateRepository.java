package vn.t3nexus.catalog.domain.product;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Kho lưu trữ trạng thái người bán tại catalog-service (INV-CAT-95).
 */
public interface SellerStateRepository {

    /**
     * Tìm trạng thái của người bán theo mã tài khoản.
     *
     * @param accountId định danh tài khoản người bán
     * @return trạng thái người bán nếu tồn tại
     */
    Optional<SellerState> find(UUID accountId);

    /**
     * Áp dụng quyết định duyệt/từ chối người bán theo phiên bản sự kiện.
     *
     * @param accountId định danh tài khoản người bán
     * @param status    trạng thái duyệt mới
     * @param version   phiên bản sự kiện
     * @param now       thời điểm cập nhật
     * @return true nếu ghi/ghi đè vì phiên bản mới hơn; false nếu bỏ qua
     */
    boolean applyDecision(UUID accountId, SellerStatus status, long version, Instant now);
}
