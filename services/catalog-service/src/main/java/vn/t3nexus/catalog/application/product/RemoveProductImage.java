package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoveProductImage implements CommandHandler<RemoveProductImage.Command, RemoveProductImage.Result> {

    private final ProductRepository productRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findById(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.removeImage(ProductImageId.of(command.imageId()));
        productRepository.save(product);
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());

        log.info("[RemoveProductImage] removed: productId={}, imageId={}, traceId={}",
                command.productId(), command.imageId(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String productId, String imageId) {}

    public record Result() {}
}
