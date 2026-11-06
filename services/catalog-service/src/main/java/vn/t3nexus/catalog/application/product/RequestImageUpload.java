package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.upload.UploadSlots;

import java.util.UUID;

/**
 * Người bán xin cấp chỗ tải lên ảnh sản phẩm (CAP-CAT-08). Không khóa dòng sản phẩm, không mở giao dịch quanh lệnh
 * (issue tự quản lý giao dịch của nó).
 */
@Service
public class RequestImageUpload implements CommandHandler<RequestImageUpload.Command, UploadSlotView> {

    private final ProductRepository products;
    private final UploadSlots uploads;
    private final ImageLimits imageLimits;

    public RequestImageUpload(ProductRepository products, UploadSlots uploads, ImageLimits imageLimits) {
        this.products = products;
        this.uploads = uploads;
        this.imageLimits = imageLimits;
    }

    @Override
    public UploadSlotView handle(Command command) {
        command.actor().requireSeller();
        UUID sellerId = command.actor().accountId();
        Product product = products.findOwned(ProductId.of(command.productId()), sellerId)
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        if (product.getImages().size() >= imageLimits.max()) {
            throw new DomainException(ProductErrorCode.IMAGE_LIMIT_REACHED);
        }

        UploadSlots.Issued issued = uploads.issue(
                new UploadSlots.IssueRequest("product-image", sellerId, command.productId(), command.contentType(), command.size())
        );

        return new UploadSlotView(issued.slotId(), issued.url(), issued.fields(), issued.expiresAt(), issued.maxSize());
    }

    public record Command(CatalogActor actor, UUID productId, String contentType, long size) {
    }
}
