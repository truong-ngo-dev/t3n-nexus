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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sắp xếp lại các anh em cùng cha. Danh sách phải có đủ mọi anh em, mỗi cái đúng một lần; thiếu, thừa hoặc lặp thì
 * từ chối. Chỉ danh mục đổi vị trí mới được ghi và phát event; trả về đủ danh sách theo thứ tự mới.
 */
@Service
@RequiredArgsConstructor
public class ReorderCategories implements CommandHandler<ReorderCategories.Command, List<CategoryView>> {

    private final CategoryRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public List<CategoryView> handle(Command command) {
        command.actor().requireAdmin();
        CategoryId parentId = command.parentId() == null ? null : CategoryId.of(command.parentId());
        if (parentId != null && repository.findById(parentId).isEmpty()) {
            throw new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND);
        }

        List<Category> siblings = repository.findSiblings(parentId);
        Set<UUID> requested = new HashSet<>(command.categoryIds());
        Set<UUID> current = siblings.stream().map(c -> c.getId().getValue()).collect(Collectors.toSet());
        if (requested.size() != command.categoryIds().size() || !requested.equals(current)) {
            throw new DomainException(CategoryErrorCode.CATEGORY_INVALID_ORDER);
        }

        Map<UUID, Category> byId = siblings.stream().collect(Collectors.toMap(c -> c.getId().getValue(), Function.identity()));
        List<CategoryView> result = new ArrayList<>();
        for (int position = 0; position < command.categoryIds().size(); position++) {
            Category category = byId.get(command.categoryIds().get(position));
            if (category.getSortOrder() == position) {
                result.add(CategoryView.of(category, category.version()));
                continue;
            }
            category.reorder(position, new ChangeContext(command.actor(), idGenerator.generate(), clock.instant()));
            long version = category.nextVersion();
            repository.save(category);
            eventDispatcher.dispatchAll(category.getDomainEvents());
            category.clearDomainEvents();
            result.add(CategoryView.of(category, version));
        }
        return result;
    }

    /** {@code parentId} null là các danh mục cấp một. */
    public record Command(CatalogActor actor, UUID parentId, List<UUID> categoryIds) {}
}
