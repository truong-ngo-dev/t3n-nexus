package vn.t3nexus.catalog.application.product.search_sync;

import java.util.List;

/**
 * Hạ tầng riêng cho luồng {@link ProductSearchSnapshotEvent} — tách khỏi {@code ProductRepository} vì
 * {@code search_version} không phải state của aggregate Product, chỉ là bộ đếm cho projection bên search.
 */
public interface ProductSearchSnapshotPort {

    /**
     * Tăng rồi trả về {@code search_version} của product. Phải gọi trong transaction đang mở — row lock
     * của UPDATE giữ tới cuối transaction nên 2 lần tăng đồng thời trên cùng product bị serialize.
     */
    long nextVersion(String productId);

    /** Keyset pagination theo id, chỉ product đã từng publish ({@code published_at IS NOT NULL}). */
    List<String> findPublishedIdsAfter(String afterId, int limit);
}
