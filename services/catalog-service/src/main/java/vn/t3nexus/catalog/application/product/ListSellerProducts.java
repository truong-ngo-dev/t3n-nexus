package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListSellerProducts
        implements QueryHandler<ListSellerProducts.Query, ListSellerProducts.Result> {

    private final ProductRepository productRepository;

    @Override
    public Result handle(Query query) {
        List<Product> products = productRepository.findBySellerIdPaged(
                query.sellerId(), query.page(), query.size());
        long total = productRepository.countBySellerId(query.sellerId());

        List<ProductSummaryDto> items = products.stream()
                .map(this::toSummary)
                .toList();

        return new Result(items, total, query.page(), query.size());
    }

    private ProductSummaryDto toSummary(Product product) {
        String thumbnailUrl = product.getImages().stream()
                .min(java.util.Comparator.comparingInt(img -> img.getDisplayOrder()))
                .map(img -> img.getObjectKey())
                .orElse(null);

        return new ProductSummaryDto(
                product.getId().getValue(),
                product.getName(),
                product.getStatus().name(),
                product.getCategoryId().getValue(),
                product.getBrandId().getValue(),
                thumbnailUrl,
                product.getCreatedAt()
        );
    }

    public record Query(String sellerId, int page, int size) {}

    public record Result(List<ProductSummaryDto> items, long total, int page, int size) {}

    public record ProductSummaryDto(
            String id,
            String name,
            String status,
            String categoryId,
            String brandId,
            String thumbnailObjectKey,
            java.time.Instant createdAt
    ) {}
}
