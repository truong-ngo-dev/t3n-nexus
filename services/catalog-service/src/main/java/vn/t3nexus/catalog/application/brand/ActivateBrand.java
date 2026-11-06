package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/** Bật lại thương hiệu đã ngừng dùng. */
@Service
@RequiredArgsConstructor
public class ActivateBrand implements CommandHandler<ActivateBrand.Command, BrandView> {

    private final BrandUpdater updater;

    @Override
    public BrandView handle(Command c) {
        return updater.update(c.actor(), c.brandId(), (b, ctx) -> b.activate(ctx));
    }

    public record Command(CatalogActor actor, UUID brandId) {}
}
