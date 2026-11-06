package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.domain.product.ProductRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.util.UUID;

/**
 * Xóa danh mục: chỉ khi không còn con (INV-CAT-05). Cũng chỉ khi chưa có sản phẩm nào tham chiếu danh mục (INV-CAT-98); không có khóa ngoại giữa hai aggregate, nên đua giữa xóa danh mục và tạo sản phẩm vào nó là rủi ro chấp nhận được (service.md).
 */
@Service
@RequiredArgsConstructor
public class DeleteCategory implements CommandHandler<DeleteCategory.Command, Void> {

    private final CategoryRepository repository;
    private final ProductRepository products;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public Void handle(Command command) {
        command.actor().requireAdmin();
        CategoryId id = CategoryId.of(command.categoryId());
        Category category = repository.findById(id)
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        if (repository.existsByParentId(id)) {
            throw new DomainException(CategoryErrorCode.CATEGORY_HAS_CHILDREN);
        }
        if (products.existsByCategory(command.categoryId())) {
            throw new DomainException(CategoryErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        category.markDeleted(new ChangeContext(command.actor(), idGenerator.generate(), clock.instant()));
        repository.delete(id);

        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        return null;
    }

    public record Command(CatalogActor actor, UUID categoryId) {}
}
