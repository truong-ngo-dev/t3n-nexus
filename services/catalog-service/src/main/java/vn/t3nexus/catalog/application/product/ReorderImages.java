package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * Người bán sắp xếp lại thứ tự ảnh sản phẩm (CAP-CAT-08). Khóa dòng sản phẩm, chỉ lưu khi thứ tự thay đổi.
 */
@Service
public class ReorderImages implements CommandHandler<ReorderImages.Command, List<ImageView>> {

    private final ProductRepository products;
    private final UploadSlots uploads;
    private final Clock clock;
    private final ProductEventRecorder recorder;

    public ReorderImages(ProductRepository products, UploadSlots uploads, Clock clock, ProductEventRecorder recorder) {
        this.products = products;
        this.uploads = uploads;
        this.clock = clock;
        this.recorder = recorder;
    }

    @Override
    @Transactional
    public List<ImageView> handle(Command command) {
        command.actor().requireSeller();
        UUID sellerId = command.actor().accountId();
        ProductId productId = ProductId.of(command.productId());

        Product product = products.findOwnedForUpdate(productId, sellerId)
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        List<ProductImageId> orderedIds = command.imageIds() == null ? List.of()
                : command.imageIds().stream().map(ProductImageId::of).toList();

        boolean changed = product.reorderImages(orderedIds, clock.instant());
        if (changed) {
            products.save(product);
            recorder.onChanged(product, List.of(), ProductEventRecorder.Trigger.EDIT);
        }

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

    public record Command(CatalogActor actor, UUID productId, List<UUID> imageIds) {
    }
}
