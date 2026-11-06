package vn.t3nexus.catalog.application.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Bật lại danh mục đã ngừng dùng. */
@Service
@RequiredArgsConstructor
public class ActivateCategory implements CommandHandler<ActivateCategory.Command, CategoryView> {

    private final CategoryUpdater updater;

    @Override
    public CategoryView handle(Command c) {
        return updater.update(c.actor(), c.categoryId(), (category, ctx) -> category.activate(ctx));
    }

    public record Command(CatalogActor actor, UUID categoryId) {}
}
