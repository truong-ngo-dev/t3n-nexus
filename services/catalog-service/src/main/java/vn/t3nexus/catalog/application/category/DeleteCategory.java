package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheInvalidationPublisher;
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteCategory implements CommandHandler<DeleteCategory.Command, DeleteCategory.Result> {

    private final CategoryRepository categoryRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_TREE, allEntries = true)
    public Result handle(Command command) {
        CategoryId id = CategoryId.of(command.id());

        if (categoryRepository.existsByParentId(id)) {
            throw new DomainException(CategoryErrorCode.HAS_CHILDREN);
        }
        if (categoryRepository.hasProductReference(id)) {
            throw new DomainException(CategoryErrorCode.HAS_PRODUCT_REFERENCE);
        }

        categoryRepository.delete(id);
        cacheInvalidationPublisher.clear(CacheNames.CATEGORY_TREE);

        log.info("[DeleteCategory] deleted: categoryId={}, traceId={}",
                command.id(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String id) {}

    public record Result() {}
}
