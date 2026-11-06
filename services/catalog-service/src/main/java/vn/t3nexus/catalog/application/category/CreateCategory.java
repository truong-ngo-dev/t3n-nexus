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
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.util.UUID;

/**
 * Quản trị tạo danh mục gốc hoặc con của danh mục đã có (CAP-CAT-01). Cha đang đóng vẫn tạo con được. Tên trùng trong
 * cùng cha sau chuẩn hóa thì từ chối (INV-CAT-02); cha đã là cấp ba thì từ chối (INV-CAT-01). Xếp cuối các anh em.
 */
@Service
@RequiredArgsConstructor
public class CreateCategory implements CommandHandler<CreateCategory.Command, CategoryView> {

    private final CategoryRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public CategoryView handle(Command command) {
        command.actor().requireAdmin();
        CategoryId parentId = command.parentId() == null ? null : CategoryId.of(command.parentId());
        Category parent = parentId == null ? null : repository.findById(parentId)
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        if (repository.existsByParentAndNameKey(parentId, Category.nameKeyOf(command.name()))) {
            throw new DomainException(CategoryErrorCode.CATEGORY_NAME_TAKEN);
        }

        int sortOrder = repository.findSiblings(parentId).stream()
                .mapToInt(Category::getSortOrder).max().orElse(-1) + 1;
        CategoryId id = CategoryId.of(idGenerator.generate());
        ChangeContext ctx = new ChangeContext(command.actor(), idGenerator.generate(), clock.instant());
        Category category = parent == null
                ? Category.createRoot(id, command.name(), sortOrder, ctx)
                : Category.createChild(id, command.name(), parentId, parent.getLevel(), sortOrder, ctx);
        long version = category.nextVersion();
        repository.save(category);

        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        return CategoryView.of(category, version);
    }

    /** {@code parentId} null là danh mục cấp một. */
    public record Command(CatalogActor actor, String name, UUID parentId) {}
}
