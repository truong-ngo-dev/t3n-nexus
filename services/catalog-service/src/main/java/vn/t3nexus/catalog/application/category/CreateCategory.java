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
import vn.t3nexus.catalog.domain.category.CategoryLevel;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateCategory implements CommandHandler<CreateCategory.Command, CreateCategory.Result> {

    private final CategoryRepository categoryRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_TREE, allEntries = true)
    public Result handle(Command command) {
        if (categoryRepository.existsBySlug(command.slug())) {
            throw new DomainException(CategoryErrorCode.CATEGORY_SLUG_EXISTS);
        }

        CategoryId newId = CategoryId.of(ulidGenerator.generate());
        Category category;

        if (command.parentId() == null) {
            category = Category.createRoot(newId, command.name(), command.slug());
        } else {
            CategoryId parentId = CategoryId.of(command.parentId());
            Category parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));
            category = Category.createChild(newId, command.name(), command.slug(),
                    parentId, parent.getLevel());
        }

        categoryRepository.save(category);
        cacheInvalidationPublisher.clear(CacheNames.CATEGORY_TREE);

        log.info("[CreateCategory] created: categoryId={}, slug={}, traceId={}",
                newId.getValue(), command.slug(), MDC.get("traceId"));

        return new Result(newId.getValue());
    }

    public record Command(String name, String slug, String parentId) {}

    public record Result(String id) {}
}
