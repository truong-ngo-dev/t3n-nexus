package vn.t3nexus.catalog.infrastructure.adapter.repository.product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.application.product.search_sync.ProductSearchSnapshotPort;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductJpaRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductSearchSnapshotAdapter implements ProductSearchSnapshotPort {

    private final ProductJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Tăng + trả về version mới trong 1 câu ({@code RETURNING}). Gọi thẳng {@link EntityManager} thay vì
     * query method của Spring Data: {@code @Modifying} chạy {@code executeUpdate} — không nhận result set
     * — còn bỏ {@code @Modifying} thì một câu UPDATE trông như query đọc. {@code getSingleResult} đọc
     * đúng result set mà {@code RETURNING} trả về. Chạy trong transaction của use case (caller MANDATORY).
     * <br>{@code search_version} không map vào entity (V15), nên câu này không đụng persistence context.
     */
    @Override
    public long nextVersion(String productId) {
        Number version = (Number) entityManager.createNativeQuery(
                        "UPDATE product SET search_version = search_version + 1 WHERE id = :id RETURNING search_version")
                .setParameter("id", productId)
                .getSingleResult();
        return version.longValue();
    }

    @Override
    public List<String> findPublishedIdsAfter(String afterId, int limit) {
        return jpaRepository.findPublishedIdsAfter(afterId, limit);
    }
}
