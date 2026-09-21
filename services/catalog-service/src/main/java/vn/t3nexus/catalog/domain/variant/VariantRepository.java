package vn.t3nexus.catalog.domain.variant;

import vn.t3nexus.lib.common.domain.service.Repository;

import java.util.List;

public interface VariantRepository extends Repository<Variant, VariantId> {

    boolean existsByProductId(String productId);

    boolean existsActiveByProductId(String productId);

    boolean existsByProductIdAndCombinationHash(String productId, String combinationHash);

    List<Variant> findByProductId(String productId);

    // Chỉ dùng khi Product còn DRAFT (DeleteProduct) — Product/Variant chưa từng publish nên chắc chắn
    // chưa có Order/lịch sử nào tham chiếu, xoá cứng an toàn. Xem service.md § Product delete.
    void deleteByProductId(String productId);
}
