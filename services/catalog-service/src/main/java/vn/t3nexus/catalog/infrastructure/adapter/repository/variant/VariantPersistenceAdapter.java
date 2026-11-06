package vn.t3nexus.catalog.infrastructure.adapter.repository.variant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.infrastructure.persistence.variant.VariantCombinationItemJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.variant.VariantCombinationItemJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.variant.VariantJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.variant.VariantJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.variant.VariantMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VariantPersistenceAdapter implements VariantRepository {

    private final VariantJpaRepository variants;
    private final VariantCombinationItemJpaRepository items;

    @Override
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void deleteByProductId(ProductId productId) {
        variants.deleteByProductId(productId.getValue());
    }

    @Override
    public Optional<Variant> findById(VariantId id) {
        return variants.findById(id.getValue()).map(entity -> toDomain(List.of(entity)).get(0));
    }

    @Override
    public Optional<Variant> findByIdAndProduct(VariantId id, ProductId productId) {
        return variants.findByIdAndProductId(id.getValue(), productId.getValue())
                .map(entity -> toDomain(List.of(entity)).get(0));
    }

    @Override
    public List<Variant> findByProductId(ProductId productId) {
        return toDomain(variants.findByProductIdOrderByCreatedAtAscIdAsc(productId.getValue()));
    }

    @Override
    public long countByProductId(ProductId productId) {
        return variants.countByProductId(productId.getValue());
    }

    @Override
    public boolean existsByProductAndCombinationKey(ProductId productId, String combinationKey) {
        return variants.existsByProductIdAndCombinationKey(productId.getValue(), combinationKey);
    }

    @Override
    public boolean existsBySellerAndSku(UUID sellerId, String sku) {
        return variants.existsBySellerIdAndSku(sellerId, sku);
    }

    @Override
    public boolean existsBySellerAndSkuExcluding(UUID sellerId, String sku, VariantId excludingId) {
        return variants.existsBySellerIdAndSkuAndIdNot(sellerId, sku, excludingId.getValue());
    }

    /**
     * {@code saveAndFlush}: ràng buộc duy nhất (tổ hợp, SKU) nổ ngay trong giao dịch (ADR-0011). Tổ hợp bất biến nên chỉ
     * ghi các cặp khi biến thể mới.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Variant variant) {
        boolean isNew = !variants.existsById(variant.getId().getValue());
        variants.saveAndFlush(VariantMapper.toJpaEntity(variant));
        if (isNew) {
            items.saveAllAndFlush(VariantMapper.toItems(variant));
        }
    }

    private List<Variant> toDomain(List<VariantJpaEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<VariantCombinationItemJpaEntity>> itemsByVariant = items
                .findByVariantIdInOrderByTemplateId(entities.stream().map(VariantJpaEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(VariantCombinationItemJpaEntity::getVariantId));
        return entities.stream()
                .map(e -> VariantMapper.toDomain(e, itemsByVariant.getOrDefault(e.getId(), List.of())))
                .toList();
    }
}
