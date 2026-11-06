package vn.t3nexus.catalog.application.variant;

import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Kho trong bộ nhớ cho test use case. Tính duy nhất tổ hợp và SKU kiểm sớm như kho thật; ràng buộc chốt cuối thuộc cơ sở dữ liệu. */
public class FakeVariantRepository implements VariantRepository {

    private final Map<UUID, Variant> store = new LinkedHashMap<>();
    public int saveCount;

    @Override
    public Optional<Variant> findById(VariantId id) {
        return Optional.ofNullable(store.get(id.getValue()));
    }

    @Override
    public Optional<Variant> findByIdAndProduct(VariantId id, ProductId productId) {
        return findById(id).filter(v -> v.getProductId().equals(productId));
    }

    @Override
    public List<Variant> findByProductId(ProductId productId) {
        return store.values().stream().filter(v -> v.getProductId().equals(productId)).toList();
    }

    @Override
    public long countByProductId(ProductId productId) {
        return findByProductId(productId).size();
    }

    @Override
    public boolean existsByProductAndCombinationKey(ProductId productId, String combinationKey) {
        return findByProductId(productId).stream().anyMatch(v -> v.getCombinationKey().equals(combinationKey));
    }

    @Override
    public boolean existsBySellerAndSku(UUID sellerId, String sku) {
        return store.values().stream().anyMatch(v -> v.getSellerId().equals(sellerId) && sku.equals(v.getSku()));
    }

    @Override
    public boolean existsBySellerAndSkuExcluding(UUID sellerId, String sku, VariantId excludingId) {
        return store.values().stream().anyMatch(v -> v.getSellerId().equals(sellerId) && sku.equals(v.getSku())
                && !v.getId().equals(excludingId));
    }

    @Override
    public void save(Variant variant) {
        saveCount++;
        store.put(variant.getId().getValue(), variant);
    }

    @Override
    public void deleteByProductId(ProductId productId) {
        store.values().removeIf(v -> v.getProductId().equals(productId));
    }

    public Variant stored(UUID id) {
        return store.get(id);
    }

    public int storedCount() {
        return store.size();
    }
}
