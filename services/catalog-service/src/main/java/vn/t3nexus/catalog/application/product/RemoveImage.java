package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductImageId;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.upload.UploadSlots;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Người bán xóa ảnh khỏi sản phẩm (CAP-CAT-08). Khóa dòng sản phẩm, lưu sản phẩm và gọi hủy đối tượng trong kho sau commit.
 */
@Service
public class RemoveImage implements CommandHandler<RemoveImage.Command, List<ImageView>> {

    private final ProductRepository products;
    private final UploadSlots uploads;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ProductEventRecorder recorder;

    public RemoveImage(ProductRepository products, UploadSlots uploads, Clock clock,
                       TransactionTemplate transactionTemplate, ProductEventRecorder recorder) {
        this.products = products;
        this.uploads = uploads;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.recorder = recorder;
    }

    @Override
    public List<ImageView> handle(Command command) {
        command.actor().requireSeller();
        UUID sellerId = command.actor().accountId();
        ProductId productId = ProductId.of(command.productId());
        ProductImageId imageId = ProductImageId.of(command.imageId());

        ProductImage removed = transactionTemplate.execute(status -> {
            Product product = products.findOwnedForUpdate(productId, sellerId)
                    .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

            ProductImage img = product.removeImage(imageId, clock.instant());
            products.save(product);
            recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.EDIT);
            return img;
        });

        uploads.discard("product-image", removed.getObjectKey());

        // Lấy lại danh sách ảnh còn lại từ bản lưu (hoặc đọc lại)
        Product product = products.findOwned(productId, sellerId)
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return product.getImages().stream()
                .map(img -> new ImageView(
                        img.getId().getValue(),
                        uploads.urlOf("product-image", img.getObjectKey()),
                        img.getPosition(),
                        img.getContentType(),
                        img.getSizeBytes()
                ))
                .toList();
    }

    public record Command(CatalogActor actor, UUID productId, UUID imageId) {
    }
}
