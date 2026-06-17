package vn.t3nexus.catalog.infrastructure.adapter.repository.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.infrastructure.persistence.product.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository jpaRepository;
    private final ProductAttributeValueJpaRepository attributeValueRepository;
    private final ProductImageJpaRepository imageRepository;

    @Override
    public Optional<Product> findById(ProductId id) {
        String rawId = id.getValue();
        return jpaRepository.findById(rawId).map(entity -> {
            List<ProductAttributeValueJpaEntity> attrs  = attributeValueRepository.findByProductId(rawId);
            List<ProductImageJpaEntity>          images = imageRepository.findByProductId(rawId);
            return ProductMapper.toDomain(entity, attrs, images);
        });
    }

    @Override
    @Transactional
    public void save(Product product) {
        String rawId = product.getId().getValue();
        jpaRepository.save(ProductMapper.toJpaEntity(product));

        attributeValueRepository.deleteByProductId(rawId);
        List<ProductAttributeValueJpaEntity> attrEntities = ProductMapper.toAttributeValueEntities(product);
        if (!attrEntities.isEmpty()) {
            attributeValueRepository.saveAll(attrEntities);
        }

        imageRepository.deleteByProductId(rawId);
        List<ProductImageJpaEntity> imageEntities = ProductMapper.toImageEntities(product);
        if (!imageEntities.isEmpty()) {
            imageRepository.saveAll(imageEntities);
        }
    }

    @Override
    @Transactional
    public void delete(ProductId id) {
        String rawId = id.getValue();
        imageRepository.deleteByProductId(rawId);
        attributeValueRepository.deleteByProductId(rawId);
        jpaRepository.deleteById(rawId);
    }

    @Override
    public List<Product> findBySellerIdPaged(String sellerId, int page, int size) {
        List<ProductJpaEntity> entities =
                jpaRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, PageRequest.of(page, size));

        List<String> productIds = entities.stream().map(ProductJpaEntity::getId).toList();

        Map<String, List<ProductAttributeValueJpaEntity>> attrsByProduct =
                attributeValueRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(ProductAttributeValueJpaEntity::getProductId));

        Map<String, List<ProductImageJpaEntity>> imagesByProduct =
                imageRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(ProductImageJpaEntity::getProductId));

        return entities.stream()
                .map(e -> ProductMapper.toDomain(
                        e,
                        attrsByProduct.getOrDefault(e.getId(), List.of()),
                        imagesByProduct.getOrDefault(e.getId(), List.of())))
                .toList();
    }

    @Override
    public boolean existsByBrandId(String brandId) {
        return jpaRepository.existsByBrandId(brandId);
    }

    @Override
    public long countBySellerId(String sellerId) {
        return jpaRepository.countBySellerId(sellerId);
    }
}
