package vn.t3nexus.catalog.infrastructure.adapter.repository.variant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.attributetemplate.AttributeOptionId;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.domain.variant.VariantStatus;
import vn.t3nexus.catalog.infrastructure.persistence.variant.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VariantPersistenceAdapter implements VariantRepository {

    private final VariantJpaRepository jpaRepository;
    private final VariantCombinationItemJpaRepository combinationItemRepository;
    private final SkuImageJpaRepository skuImageRepository;

    @Override
    public Optional<Variant> findById(VariantId id) {
        String rawId = id.getValue();
        return jpaRepository.findById(rawId).map(entity -> {
            List<VariantCombinationItemJpaEntity> items  = combinationItemRepository.findByVariantId(rawId);
            List<SkuImageJpaEntity>               images = skuImageRepository.findByVariantId(rawId);
            return VariantMapper.toDomain(entity, items, images);
        });
    }

    @Override
    @Transactional
    public void save(Variant variant) {
        String rawId = variant.getId().getValue();
        boolean isNew = !jpaRepository.existsById(rawId);

        jpaRepository.save(VariantMapper.toJpaEntity(variant));

        if (isNew) {
            List<VariantCombinationItemJpaEntity> items = VariantMapper.toCombinationItems(variant);
            if (!items.isEmpty()) {
                combinationItemRepository.saveAll(items);
            }
        }

        skuImageRepository.deleteByVariantId(rawId);
        List<SkuImageJpaEntity> imageEntities = VariantMapper.toSkuImageEntities(variant);
        if (!imageEntities.isEmpty()) {
            skuImageRepository.saveAll(imageEntities);
        }
    }

    @Override
    @Transactional
    public void delete(VariantId id) {
        String rawId = id.getValue();
        skuImageRepository.deleteByVariantId(rawId);
        combinationItemRepository.deleteByVariantId(rawId);
        jpaRepository.deleteById(rawId);
    }

    @Override
    public boolean existsByOptionId(AttributeOptionId optionId) {
        return combinationItemRepository.existsByOptionId(optionId.getValue());
    }

    @Override
    public boolean existsByProductId(String productId) {
        return jpaRepository.existsByProductId(productId);
    }

    @Override
    public boolean existsActiveByProductId(String productId) {
        return jpaRepository.existsByProductIdAndStatus(productId, VariantStatus.ACTIVE);
    }

    @Override
    public boolean existsByProductIdAndCombinationHash(String productId, String combinationHash) {
        return jpaRepository.existsByProductIdAndCombinationHash(productId, combinationHash);
    }

    @Override
    public List<Variant> findByProductId(String productId) {
        List<VariantJpaEntity> entities = jpaRepository.findByProductId(productId);
        if (entities.isEmpty()) return List.of();

        List<String> variantIds = entities.stream().map(VariantJpaEntity::getId).toList();

        Map<String, List<VariantCombinationItemJpaEntity>> itemsByVariant =
                combinationItemRepository.findByVariantIdIn(variantIds).stream()
                        .collect(Collectors.groupingBy(VariantCombinationItemJpaEntity::getVariantId));

        Map<String, List<SkuImageJpaEntity>> imagesByVariant =
                skuImageRepository.findByVariantIdIn(variantIds).stream()
                        .collect(Collectors.groupingBy(SkuImageJpaEntity::getVariantId));

        return entities.stream()
                .map(e -> VariantMapper.toDomain(
                        e,
                        itemsByVariant.getOrDefault(e.getId(), List.of()),
                        imagesByVariant.getOrDefault(e.getId(), List.of())))
                .toList();
    }
}
