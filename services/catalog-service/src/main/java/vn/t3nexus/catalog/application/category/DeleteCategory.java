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

/** Xoá hẳn — công cụ hoàn tác cho danh mục tạo sai vị trí/trùng lặp, chưa ai dùng (analysis.md AGG-CAT-03). */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteCategory implements CommandHandler<DeleteCategory.Command, DeleteCategory.Result> {

    private final CategoryRepository categoryRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_TREE, allEntries = true)
    public Result handle(Command command) {
        CategoryId id = CategoryId.of(command.id());
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        if (categoryRepository.existsByParentId(id)) {
            throw new DomainException(CategoryErrorCode.HAS_CHILDREN);
        }
        // Không có FK product → category (service.md T-09) — race với CreateProduct được chấp nhận (C-05).
        if (categoryRepository.hasProductReference(id)) {
            throw new DomainException(CategoryErrorCode.HAS_PRODUCT_REFERENCE);
        }

        category.markDeleted();
        categoryRepository.delete(id);
        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        cacheInvalidationPublisher.clear(CacheNames.CATEGORY_TREE);

        log.info("[DeleteCategory] deleted: categoryId={}, traceId={}", command.id(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String id) {}

    public record Result() {}
}
