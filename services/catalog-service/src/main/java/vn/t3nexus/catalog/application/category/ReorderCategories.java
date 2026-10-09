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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Sắp lại thứ tự anh em cùng cha ({@code parentId} null = root) — gửi đủ mọi anh em theo thứ tự mong muốn,
 *  mỗi anh em là 1 aggregate riêng nên phải load + save từng cái (khác {@code ReorderAttributeOptions}). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReorderCategories implements CommandHandler<ReorderCategories.Command, ReorderCategories.Result> {

    private final CategoryRepository categoryRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.CATEGORY_TREE, allEntries = true)
    public Result handle(Command command) {
        CategoryId parentId = command.parentId() != null ? CategoryId.of(command.parentId()) : null;
        List<Category> siblings = categoryRepository.findSiblings(parentId);

        Set<String> requested = new HashSet<>(command.categoryIds());
        Set<String> current = siblings.stream().map(c -> c.getId().getValue()).collect(Collectors.toSet());
        if (requested.size() != command.categoryIds().size() || !requested.equals(current)) {
            throw new DomainException(CategoryErrorCode.INVALID_CATEGORY_ORDER);
        }

        for (int i = 0; i < command.categoryIds().size(); i++) {
            String id = command.categoryIds().get(i);
            Category category = siblings.stream()
                    .filter(c -> c.getId().getValue().equals(id))
                    .findFirst()
                    .orElseThrow(); // không thể xảy ra — đã kiểm set trùng khớp ở trên
            category.reorder(i);
            categoryRepository.save(category);
            eventDispatcher.dispatchAll(category.getDomainEvents());
            category.clearDomainEvents();
        }
        cacheInvalidationPublisher.clear(CacheNames.CATEGORY_TREE);

        log.info("[ReorderCategories] reordered: parentId={}, count={}, traceId={}",
                command.parentId(), command.categoryIds().size(), MDC.get("traceId"));

        return new Result();
    }

    public record Command(String parentId, List<String> categoryIds) {}

    public record Result() {}
}
