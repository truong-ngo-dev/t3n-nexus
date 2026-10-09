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

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateCategory
        implements CommandHandler<DeactivateCategory.Command, DeactivateCategory.Result> {

    private final CategoryRepository categoryRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_TREE, allEntries = true)
    public Result handle(Command command) {
        Category category = categoryRepository.findById(CategoryId.of(command.id()))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        category.deactivate();
        categoryRepository.save(category);
        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        // L1 của instance khác — thiếu thì danh mục đã đóng vẫn hiện trên cây tới hết TTL L1.
        cacheInvalidationPublisher.clear(CacheNames.CATEGORY_TREE);

        log.info("[DeactivateCategory] deactivated: categoryId={}, traceId={}", command.id(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String id) {}

    public record Result() {}
}
