package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmProductImageUpload
        implements CommandHandler<ConfirmProductImageUpload.Command, ConfirmProductImageUpload.Result> {

    private final ProductRepository productRepository;
    private final ObjectStoragePort objectStoragePort;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.PRODUCT, key = "#command.productId()")
    public Result handle(Command command) {
        Product product = productRepository.findById(ProductId.of(command.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (!objectStoragePort.objectExists(command.objectKey())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Uploaded object not found in storage: " + command.objectKey());
        }

        int displayOrder = product.getImages().size();
        ProductImage image = ProductImage.create(
                ProductImageId.of(ulidGenerator.generate()),
                command.objectKey(),
                displayOrder);

        product.addImage(image);
        productRepository.save(product);
        cacheInvalidationPublisher.evict(CacheNames.PRODUCT, command.productId());

        log.info("[ConfirmProductImageUpload] confirmed: productId={}, objectKey={}, traceId={}",
                command.productId(), command.objectKey(), MDC.get("traceId"));

        return new Result(image.getId().getValue());
    }

    public record Command(String productId, String objectKey) {}

    public record Result(String imageId) {}
}
