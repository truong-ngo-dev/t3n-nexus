package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.variant.Variant;
import vn.t3nexus.catalog.domain.variant.VariantDeletedEvent;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

import java.util.List;

/**
 * Hard-delete Product — chỉ cho phép khi còn DRAFT (chưa từng publish, nên chắc chắn chưa có Order nào
 * tham chiếu tới bất kỳ Variant nào của nó). Cascade xoá luôn toàn bộ Variant — Product là Aggregate Root
 * khác Variant nên orchestrate tường minh ở đây, không tự cascade trong persistence adapter của Product.
 * Phát {@link VariantDeletedEvent} cho từng Variant bị xoá — inventory-service consume để dọn Stock row
 * mồ côi (đã tạo sẵn từ VariantCreatedEvent lúc AddVariant, kể cả khi Product còn DRAFT).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteProduct implements CommandHandler<DeleteProduct.Command, DeleteProduct.Result> {

    private final ProductRepository productRepository;
    private final VariantRepository variantRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = {CacheNames.PRODUCT, CacheNames.PRODUCT_VARIANTS}, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findById(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.assertOwnedBy(command.sellerId());

        product.markDeleted(); // INV-CAT-043 — chỉ bản nháp; phát ProductDeleted (EVT-CAT-046)

        List<Variant> variants = variantRepository.findByProductId(command.productId());

        variantRepository.deleteByProductId(command.productId());
        productRepository.delete(product.getId());
        eventDispatcher.dispatchAll(product.getDomainEvents());
        product.clearDomainEvents();

        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT_VARIANTS, command.productId());

        variants.forEach(variant -> eventDispatcher.dispatch(
                new VariantDeletedEvent(variant.getId().getValue(), command.productId())));

        log.info("[DeleteProduct] deleted: productId={}, variantCount={}, traceId={}",
                command.productId(), variants.size(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String sellerId, String productId) {}

    public record Result() {}
}
