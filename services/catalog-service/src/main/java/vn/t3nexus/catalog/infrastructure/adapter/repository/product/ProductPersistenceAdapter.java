package vn.t3nexus.catalog.infrastructure.adapter.repository.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductAttribute;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.ProductStatus;
import vn.t3nexus.catalog.domain.product.ProductSummary;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductAttributeJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductAttributeJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductAttributeOptionJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductAttributeOptionJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductImageJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductImageJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductJpaEntity;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.product.ProductMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository products;
    private final ProductAttributeJpaRepository attributes;
    private final ProductAttributeOptionJpaRepository options;
    private final ProductImageJpaRepository images;

    @Override
    public Optional<Product> findById(ProductId id) {
        return products.findById(id.getValue()).map(this::toDomain);
    }

    @Override
    public Optional<Product> findOwned(ProductId id, UUID sellerId) {
        return products.findByIdAndSellerId(id.getValue(), sellerId).map(this::toDomain);
    }

    /** Cần giao dịch đang mở (khóa dòng sống tới hết giao dịch); use case luôn có {@code @Transactional}. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Product> findOwnedForUpdate(ProductId id, UUID sellerId) {
        return products.findForUpdate(id.getValue(), sellerId).map(this::toDomain);
    }

    @Override
    public List<ProductSummary> findSummaries(UUID sellerId, ProductStatus status, int page, int size) {
        return products.findSummaries(sellerId, status, PageRequest.of(page, size)).stream()
                .map(row -> new ProductSummary((UUID) row[0], (String) row[1], (ProductStatus) row[2],
                        (Long) row[3], (Long) row[4], (Long) row[5], (Long) row[6], (Instant) row[7]))
                .toList();
    }

    @Override
    public long countBySeller(UUID sellerId, ProductStatus status) {
        return products.countBySeller(sellerId, status);
    }

    @Override
    public boolean existsByCategory(UUID categoryId) {
        return products.existsByCategoryId(categoryId);
    }

    /**
     * {@code saveAndFlush}: khóa lạc quan qua {@code version} nổ ngay trong giao dịch (ADR-0011). Thuộc tính và ảnh ghi lại
     * (xóa rồi chèn) chỉ khi khác bản đã lưu, để sửa tiêu đề không đụng bảng con.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Product product) {
        products.saveAndFlush(ProductMapper.toJpaEntity(product));
        UUID id = product.getId().getValue();
        saveAttributes(product, id);
        saveImages(product, id);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(ProductId id) {
        products.deleteById(id.getValue());
        products.flush();
    }

    private void saveAttributes(Product product, UUID id) {
        List<ProductAttribute> stored = ProductMapper.toAttributes(attributes.findByProductIdOrderByTemplateId(id),
                options.findByProductIdOrderByTemplateIdAscOptionIdAsc(id));
        if (ProductMapper.asSet(stored).equals(ProductMapper.asSet(product.getAttributes()))) {
            return;
        }
        attributes.deleteByProductId(id);
        attributes.saveAllAndFlush(ProductMapper.toAttributeEntities(product));
        options.saveAllAndFlush(ProductMapper.toOptionEntities(product));
    }

    private void saveImages(Product product, UUID id) {
        if (sameImages(images.findByProductIdOrderByPosition(id), product.getImages())) {
            return;
        }
        images.deleteByProductId(id);
        images.saveAllAndFlush(ProductMapper.toImageEntities(product));
    }

    /** Cùng danh sách khi cùng mã ảnh ở cùng vị trí (ảnh bất biến sau khi thêm). */
    private static boolean sameImages(List<ProductImageJpaEntity> stored, List<ProductImage> wanted) {
        if (stored.size() != wanted.size()) {
            return false;
        }
        for (int i = 0; i < stored.size(); i++) {
            if (!stored.get(i).getId().equals(wanted.get(i).getId().getValue())
                    || stored.get(i).getPosition() != wanted.get(i).getPosition()) {
                return false;
            }
        }
        return true;
    }

    private Product toDomain(ProductJpaEntity entity) {
        List<ProductAttributeJpaEntity> attributeRows = attributes.findByProductIdOrderByTemplateId(entity.getId());
        List<ProductAttributeOptionJpaEntity> optionRows =
                options.findByProductIdOrderByTemplateIdAscOptionIdAsc(entity.getId());
        return ProductMapper.toDomain(entity, attributeRows, optionRows,
                images.findByProductIdOrderByPosition(entity.getId()));
    }
}
