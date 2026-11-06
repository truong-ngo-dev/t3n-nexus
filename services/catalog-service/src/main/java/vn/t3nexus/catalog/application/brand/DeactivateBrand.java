package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;

import java.util.UUID;

/**
 * Ngừng dùng thương hiệu: chỉ chặn chọn cho sản phẩm mới. Sản phẩm đang dùng giữ nhãn; không kiểm có sản phẩm dùng hay
 * không (INV-CAT-21).
 */
@Service
@RequiredArgsConstructor
public class DeactivateBrand implements CommandHandler<DeactivateBrand.Command, BrandView> {

    private final BrandUpdater updater;

    @Override
    public BrandView handle(Command c) {
        return updater.update(c.actor(), c.brandId(), (b, ctx) -> b.deactivate(ctx));
    }

    public record Command(CatalogActor actor, UUID brandId) {}
}
