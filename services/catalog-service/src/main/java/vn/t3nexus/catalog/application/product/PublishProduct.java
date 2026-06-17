package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishProduct implements CommandHandler<PublishProduct.Command, PublishProduct.Result> {

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final BrandRepository brandRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findById(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!variantRepository.existsActiveByProductId(command.productId())) {
            throw new DomainException(ProductErrorCode.PUBLISH_REQUIRES_ACTIVE_VARIANT);
        }

        List<String> activeSkuIds = variantRepository.findByProductId(command.productId()).stream()
                .filter(Variant::isActive)
                .map(v -> v.getId().getValue())
                .toList();

        Brand brand = brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));

        product.publish(brand.getName(), activeSkuIds);
        productRepository.save(product);
        eventDispatcher.dispatchAll(product.getDomainEvents());
        product.clearDomainEvents();
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());

        log.info("[PublishProduct] published: productId={}, traceId={}", command.productId(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String productId) {}

    public record Result() {}
}
