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
 * Đổi tên danh mục, làm được cả khi đang ngừng dùng. Tên của anh em cùng cha thì từ chối (INV-CAT-02); đổi cách viết
 * của chính nó (hoa thường, dấu) thì được.
 */
@Service
@RequiredArgsConstructor
public class RenameCategory implements CommandHandler<RenameCategory.Command, CategoryView> {

    private final CategoryRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public CategoryView handle(Command command) {
        command.actor().requireAdmin();
        CategoryId id = CategoryId.of(command.categoryId());
        Category category = repository.findById(id)
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        if (repository.existsByParentAndNameKeyExcludingId(category.getParentId(),
                Category.nameKeyOf(command.name()), id)) {
            throw new DomainException(CategoryErrorCode.CATEGORY_NAME_TAKEN);
        }

        category.rename(command.name(), new ChangeContext(command.actor(), idGenerator.generate(), clock.instant()));
        long version = category.nextVersion();
        repository.save(category);

        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        return CategoryView.of(category, version);
    }

    public record Command(CatalogActor actor, UUID categoryId, String name) {}
}
