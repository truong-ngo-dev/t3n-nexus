package vn.t3nexus.catalog.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.product.*;
import vn.t3nexus.lib.common.domain.cqrs.QueryHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Service
@RequiredArgsConstructor
public class GetProductImageUploadUrl
        implements QueryHandler<GetProductImageUploadUrl.Query, GetProductImageUploadUrl.Result> {

    private static final int PRESIGNED_URL_TTL_SECONDS = 300;

    private final ProductRepository productRepository;
    private final ObjectStoragePort objectStoragePort;

    @Override
    public Result handle(Query query) {
        productRepository.findById(ProductId.of(query.productId()))
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        String objectKey = "products/" + query.productId() + "/" + java.util.UUID.randomUUID();
        String uploadUrl = objectStoragePort.generatePresignedPutUrl(objectKey, PRESIGNED_URL_TTL_SECONDS);

        return new Result(uploadUrl, objectKey, PRESIGNED_URL_TTL_SECONDS);
    }

    public record Query(String productId) {}

    public record Result(String uploadUrl, String objectKey, int ttlSeconds) {}
}
