package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProduct implements QueryHandler<GetProduct.Query, GetProduct.Result> {

    private final ProductRepository productRepository;

    @Override
    @Cacheable(value = CacheNames.PRODUCT, key = "#query.productId()")
    public Result handle(Query query) {
        Product product = productRepository.findById(ProductId.of(query.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return toResult(product);
    }

    private Result toResult(Product product) {
        List<AttributeValueDto> attrDtos = product.getAttributeValues().stream()
                .map(av -> new AttributeValueDto(av.templateId().getValue(), av.value()))
                .toList();

        List<ImageDto> imageDtos = product.getImages().stream()
                .map(img -> new ImageDto(img.getId().getValue(), img.getObjectKey(), img.getDisplayOrder()))
                .toList();

        WarrantyDto warrantyDto = product.getWarrantyInfo() == null ? null
                : new WarrantyDto(
                        product.getWarrantyInfo().months(),
                        product.getWarrantyInfo().type(),
                        product.getWarrantyInfo().coverage());

        return new Result(
                product.getId().getValue(),
                product.getSellerId(),
                product.getCategoryId().getValue(),
                product.getBrandId().getValue(),
                product.getName(),
                product.getDescription(),
                product.getStatus().name(),
                warrantyDto,
                attrDtos,
                imageDtos
        );
    }

    public record Query(String productId) {}

    public record Result(
            String id,
            String sellerId,
            String categoryId,
            String brandId,
            String name,
            String description,
            String status,
            WarrantyDto warrantyInfo,
            List<AttributeValueDto> attributeValues,
            List<ImageDto> images
    ) {}

    public record AttributeValueDto(String templateId, String value) {}

    public record ImageDto(String imageId, String objectKey, int displayOrder) {}

    public record WarrantyDto(int months, String type, String coverage) {}
}
