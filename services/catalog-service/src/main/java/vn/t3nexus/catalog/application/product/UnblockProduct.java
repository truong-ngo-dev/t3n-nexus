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
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnblockProduct implements CommandHandler<UnblockProduct.Command, UnblockProduct.Result> {

    private final ProductRepository productRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findById(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        product.unblock();
        productRepository.save(product);
        eventDispatcher.dispatchAll(product.getDomainEvents());
        product.clearDomainEvents();
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());

        log.info("[UnblockProduct] unblocked: productId={}, traceId={}", command.productId(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String productId) {}

    public record Result() {}
}
