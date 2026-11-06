package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.category.Category;
import vn.t3nexus.catalog.domain.category.CategoryErrorCode;
import vn.t3nexus.catalog.domain.category.CategoryId;
import vn.t3nexus.catalog.domain.category.CategoryRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Khuôn chung của lệnh sửa một danh mục có sẵn mà không cần đọc thêm gì: kiểm vai ADMIN, đọc danh mục, gọi hành vi
 * miền, lưu, phát event đồng bộ (kiểm toán và hộp thư cùng giao dịch). Mỗi use case chỉ nêu hành vi miền cần gọi.
 */
@Component
@RequiredArgsConstructor
public class CategoryUpdater {

    private final CategoryRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Transactional
    public CategoryView update(CatalogActor actor, UUID categoryId, BiConsumer<Category, ChangeContext> change) {
        actor.requireAdmin();
        Category category = repository.findById(CategoryId.of(categoryId))
                .orElseThrow(() -> new DomainException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        change.accept(category, new ChangeContext(actor, idGenerator.generate(), clock.instant()));
        long version = category.nextVersion();
        repository.save(category);

        eventDispatcher.dispatchAll(category.getDomainEvents());
        category.clearDomainEvents();
        return CategoryView.of(category, version);
    }
}
