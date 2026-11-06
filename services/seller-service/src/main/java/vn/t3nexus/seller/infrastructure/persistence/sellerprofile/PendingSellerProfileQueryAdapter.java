package vn.t3nexus.seller.infrastructure.persistence.sellerprofile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.seller.application.sellerprofile.PendingSellerProfileQuery;
import vn.t3nexus.seller.domain.sellerprofile.SellerProfileStatus;

import java.util.List;

/**
 * Adapter triển khai cổng đọc {@link PendingSellerProfileQuery} ở tầng lưu trữ.
 */
@Repository
public class PendingSellerProfileQueryAdapter implements PendingSellerProfileQuery {

    private final SellerProfileJpaRepository jpaRepository;

    public PendingSellerProfileQueryAdapter(SellerProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Result find(int page, int size) {
        Page<SellerProfileJpaRepository.PendingRow> springPage = jpaRepository.findPending(
                SellerProfileStatus.PENDING_REVIEW,
                PageRequest.of(page, size)
        );

        List<Item> items = springPage.getContent().stream()
                .map(row -> new Item(row.id(), row.sellerType(), row.shopName(), row.submittedAt()))
                .toList();

        return new Result(items, page, size, springPage.getTotalElements());
    }
}
