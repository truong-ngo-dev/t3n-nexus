package vn.t3nexus.catalog.application.product;

import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.product.ProductSummary;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kho trong bộ nhớ cho test use case. Lưu bản sao có phiên bản tăng như cơ sở dữ liệu thật (0 khi lưu lần đầu) và ghi
 * lại mỗi lần đọc là đọc thường hay đọc khóa dòng, để test chứng minh lệnh nào gọi đường nào (ADR-0012).
 */
public class FakeProductRepository implements ProductRepository {

    private final Map<UUID, Product> store = new LinkedHashMap<>();
    public final Set<UUID> categoriesInUse = new HashSet<>();
    public final List<String> reads = new ArrayList<>();
    public int saveCount;
    public List<ProductSummary> summaries = List.of();
    public long total;

    @Override
    public Optional<Product> findById(ProductId id) {
        return Optional.ofNullable(store.get(id.getValue()));
    }

    @Override
    public Optional<Product> findOwned(ProductId id, UUID sellerId) {
        reads.add("plain");
        return owned(id, sellerId);
    }

    @Override
    public Optional<Product> findOwnedForUpdate(ProductId id, UUID sellerId) {
        reads.add("lock");
        return owned(id, sellerId);
    }

    @Override
    public List<ProductSummary> findSummaries(UUID sellerId, ProductStatus status, int page, int size) {
        return summaries;
    }

    @Override
    public long countBySeller(UUID sellerId, ProductStatus status) {
        return total;
    }

    @Override
    public boolean existsByCategory(UUID categoryId) {
        return categoriesInUse.contains(categoryId)
                || store.values().stream().anyMatch(p -> categoryId.equals(p.getCategoryId().getValue()));
    }

    @Override
    public void save(Product p) {
        saveCount++;
        store.put(p.getId().getValue(), Product.reconstitute(p.getId(), p.getSellerId(), p.getCategoryId(),
                p.getBrandId(), p.getTitle(), p.getDescription(), p.getStatus(), p.getAttributes(), p.getImages(),
                p.getPublishedAt(), p.nextVersion(), p.getCreatedAt(), p.getUpdatedAt()));
    }

    public final List<UUID> deleted = new ArrayList<>();

    @Override
    public void delete(ProductId id) {
        deleted.add(id.getValue());
        store.remove(id.getValue());
    }

    public Product stored(UUID id) {
        return store.get(id);
    }

    public int storedCount() {
        return store.size();
    }

    private Optional<Product> owned(ProductId id, UUID sellerId) {
        return Optional.ofNullable(store.get(id.getValue())).filter(p -> p.ownedBy(sellerId));
    }
}
