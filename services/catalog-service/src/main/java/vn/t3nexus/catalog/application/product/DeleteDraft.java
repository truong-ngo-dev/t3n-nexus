package vn.t3nexus.catalog.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.product.Product;
import vn.t3nexus.catalog.domain.product.ProductErrorCode;
import vn.t3nexus.catalog.domain.product.ProductId;
import vn.t3nexus.catalog.domain.product.ProductImage;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.catalog.domain.variant.VariantRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.upload.UploadSlots;

import java.util.List;
import java.util.UUID;

/**
 * Người bán xóa bản nháp sản phẩm chưa từng đăng (CAP-CAT-07).
 * Khóa dòng sản phẩm, kiểm tra tính hợp lệ, xóa biến thể và sản phẩm trong giao dịch,
 * sau đó hủy các đối tượng ảnh tương ứng ngoài giao dịch.
 */
@Service
public class DeleteDraft implements CommandHandler<DeleteDraft.Command, Void> {

    private final ProductRepository products;
    private final VariantRepository variantRepository;
    private final UploadSlots uploads;
    private final TransactionTemplate transactionTemplate;

    public DeleteDraft(ProductRepository products, VariantRepository variantRepository,
                       UploadSlots uploads, TransactionTemplate transactionTemplate) {
        this.products = products;
        this.variantRepository = variantRepository;
        this.uploads = uploads;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Void handle(Command command) {
        command.actor().requireSeller();
        UUID sellerId = command.actor().accountId();
        ProductId productId = ProductId.of(command.productId());

        List<String> imageKeys = transactionTemplate.execute(status -> {
            Product product = products.findOwnedForUpdate(productId, sellerId)
                    .orElseThrow(() -> new DomainException(ProductErrorCode.PRODUCT_NOT_FOUND));

            product.assertDeletable();

            List<String> keys = product.getImages().stream()
                    .map(ProductImage::getObjectKey)
                    .toList();

            variantRepository.deleteByProductId(productId);
            products.delete(productId);

            return keys;
        });

        if (imageKeys != null) {
            for (String key : imageKeys) {
                uploads.discard("product-image", key);
            }
        }

        return null;
    }

    public record Command(CatalogActor actor, UUID productId) {
    }
}
