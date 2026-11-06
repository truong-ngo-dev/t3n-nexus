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
import vn.t3nexus.lib.common.domain.service.IdGenerator;
import vn.t3nexus.lib.upload.UploadSlots;

import java.time.Clock;
import java.util.UUID;

/**
 * Người bán xác nhận đã tải ảnh xong để gắn vào sản phẩm (CAP-CAT-08). Hai pha:
 * Pha 1 ngoài giao dịch kiểm tra tệp với kho đối tượng;
 * Pha 2 trong giao dịch khóa dòng sản phẩm, tiêu thụ chỗ tải và thêm ảnh.
 */
@Service
public class ConfirmImage implements CommandHandler<ConfirmImage.Command, ImageView> {

    private final ProductRepository products;
    private final UploadSlots uploads;
    private final IdGenerator ids;
    private final Clock clock;
    private final ImageLimits imageLimits;
    private final TransactionTemplate transactionTemplate;
    private final ProductEventRecorder recorder;

    public ConfirmImage(ProductRepository products, UploadSlots uploads, IdGenerator ids, Clock clock,
                        ImageLimits imageLimits, TransactionTemplate transactionTemplate,
                        ProductEventRecorder recorder) {
        this.products = products;
        this.uploads = uploads;
        this.ids = ids;
        this.clock = clock;
        this.imageLimits = imageLimits;
        this.transactionTemplate = transactionTemplate;
        this.recorder = recorder;
    }

    @Override
    public ImageView handle(Command command) {
        // Pha 1: KHÔNG trong giao dịch, KHÔNG khóa
        command.actor().requireSeller();
        UUID sellerId = command.actor().accountId();
        ProductId productId = ProductId.of(command.productId());
        products.findOwned(productId, sellerId)
                .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

        UploadSlots.Verified v = uploads.verify(command.slotId(), "product-image", sellerId, command.productId());

        // Pha 2: trong TransactionTemplate
        try {
            return transactionTemplate.execute(status -> {
                Product product = products.findOwnedForUpdate(productId, sellerId)
                        .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

                uploads.consume(v);

                ProductImage image = ProductImage.of(
                        ProductImageId.of(ids.generate()),
                        v.objectKey(),
                        v.contentType(),
                        v.size(),
                        0
                );

                product.addImage(image, imageLimits.max(), clock.instant());
                products.save(product);
                recorder.onChanged(product, java.util.List.of(), ProductEventRecorder.Trigger.EDIT);

                ProductImage added = product.getImages().get(product.getImages().size() - 1);
                String url = uploads.urlOf("product-image", v.objectKey());

                return new ImageView(added.getId().getValue(), url, added.getPosition(), added.getContentType(), added.getSizeBytes());
            });
        } catch (DomainException e) {
            // Chỉ xóa đối tượng khi chắc chắn nó chưa được gắn (đủ ảnh). Lỗi ở consume nghĩa là một yêu cầu khác đã dùng chỗ này
            // và đang giữ chính đối tượng đó: xóa sẽ làm mất ảnh vừa gắn (hai yêu cầu cùng xác nhận một chỗ).
            if (e.getErrorCode() == ProductErrorCode.IMAGE_LIMIT_REACHED) {
                uploads.discard("product-image", v.objectKey());
            }
            throw e;
        }
    }

    public record Command(CatalogActor actor, UUID productId, UUID slotId) {
    }
}
