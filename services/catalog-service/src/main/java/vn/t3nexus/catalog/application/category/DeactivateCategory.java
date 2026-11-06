package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/**
 * Ngừng dùng danh mục: đóng nhận sản phẩm mới và ẩn khỏi cây duyệt. Không cascade xuống con, không kiểm có con hay có
 * sản phẩm; khác xóa hẳn.
 */
@Service
@RequiredArgsConstructor
public class DeactivateCategory implements CommandHandler<DeactivateCategory.Command, CategoryView> {

    private final CategoryUpdater updater;

    @Override
    public CategoryView handle(Command c) {
        return updater.update(c.actor(), c.categoryId(), (category, ctx) -> category.deactivate(ctx));
    }

    public record Command(CatalogActor actor, UUID categoryId) {}
}
