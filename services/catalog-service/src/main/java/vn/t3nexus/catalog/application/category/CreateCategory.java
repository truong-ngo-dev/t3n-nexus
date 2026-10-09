package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateCategory implements CommandHandler<CreateCategory.Command, CreateCategory.Result> {

    private final CategoryRepository categoryRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_TREE, allEntries = true)
    public Result handle(Command command) {
        CategoryId parentId = command.parentId() != null ? CategoryId.of(command.parentId()) : null;
        if (categoryRepository.existsByParentAndNameIgnoreCase(parentId, command.name())) {
            throw new DomainException(CategoryErrorCode.CATEGORY_NAME_EXISTS_IN_PARENT);
        }
        int sortOrder = categoryRepository.findSiblings(parentId).size();

        CategoryId newId = CategoryId.of(ulidGenerator.generate());
        Category category;

        if (parentId == null) {
            category = Category.createRoot(newId, command.name(), sortOrder);
        } else {
            Category parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
            category = Category.createChild(newId, command.name(), parentId, parent.getLevel(), sortOrder);
        }

        categoryRepository.save(category);
        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        cacheInvalidationPublisher.clear(CacheNames.CATEGORY_TREE);

        log.info("[CreateCategory] created: categoryId={}, parentId={}, traceId={}",
                newId.getValue(), command.parentId(), MDC.get("traceId"));

        return new Result(newId.getValue());
    }

    public record Command(String name, String parentId) {}

    public record Result(String id) {}
}
