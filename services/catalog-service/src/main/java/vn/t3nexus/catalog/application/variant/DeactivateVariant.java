package vn.t3nexus.catalog.application.variant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantId;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateVariant implements CommandHandler<DeactivateVariant.Command, DeactivateVariant.Result> {

    private final VariantRepository variantRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_VARIANTS, key = "#command.productId()"),
            @CacheEvict(value = CacheNames.PRODUCT, key = "#command.productId()")
    })
    public Result handle(Command command) {
        Variant variant = variantRepository.findById(VariantId.of(command.skuId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        variant.deactivate();
        variantRepository.save(variant);
        eventDispatcher.dispatchAll(variant.getDomainEvents());
        variant.clearDomainEvents();
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT_VARIANTS, command.productId());
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());

        log.info("[DeactivateVariant] deactivated: skuId={}, productId={}, traceId={}",
                command.skuId(), command.productId(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String productId, String skuId) {}

    public record Result() {}
}
