package vn.t3nexus.catalog.application.variant;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProductVariants
        implements QueryHandler<GetProductVariants.Query, GetProductVariants.Result> {

    private final VariantRepository variantRepository;

    @Override
    @Cacheable(value = CacheNames.PRODUCT_VARIANTS, key = "#query.productId()")
    public Result handle(Query query) {
        List<Variant> variants = variantRepository.findByProductId(query.productId());

        List<VariantDto> dtos = variants.stream()
                .map(this::toDto)
                .toList();

        return new Result(dtos);
    }

    private VariantDto toDto(Variant variant) {
        List<CombinationItemDto> combination = variant.getCombination().getPairs().stream()
                .map(p -> new CombinationItemDto(
                        p.templateId().getValue(),
                        p.optionId().getValue()))
                .toList();

        List<SkuImageDto> images = variant.getImages().stream()
                .map(img -> new SkuImageDto(img.getId().getValue(), img.getObjectKey(), img.getDisplayOrder()))
                .toList();

        return new VariantDto(
                variant.getId().getValue(),
                variant.getProductId(),
                combination,
                variant.getSkuCode(),
                variant.getPrice(),
                variant.getStock(),
                variant.getStatus().name(),
                images
        );
    }

    public record Query(String productId) {}

    public record Result(List<VariantDto> variants) {}

    public record VariantDto(
            String skuId,
            String productId,
            List<CombinationItemDto> combination,
            String skuCode,
            long price,
            int stock,
            String status,
            List<SkuImageDto> images
    ) {}

    public record CombinationItemDto(String templateId, String optionId) {}

    public record SkuImageDto(String imageId, String objectKey, int displayOrder) {}
}
