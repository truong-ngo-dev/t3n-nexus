package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Khuôn chung của lệnh sửa một thương hiệu có sẵn: kiểm vai ADMIN, đọc thương hiệu, gọi hành vi miền, lưu, phát event
 * đồng bộ (kiểm toán cùng giao dịch). Mỗi use case chỉ nêu hành vi miền cần gọi.
 */
@Component
@RequiredArgsConstructor
public class BrandUpdater {

    private final BrandRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Transactional
    public BrandView update(CatalogActor actor, UUID brandId, BiConsumer<Brand, ChangeContext> change) {
        actor.requireAdmin();
        Brand brand = repository.findById(BrandId.of(brandId))
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));

        change.accept(brand, new ChangeContext(actor, idGenerator.generate(), clock.instant()));
        long version = brand.nextVersion();
        repository.save(brand);

        eventDispatcher.dispatchAll(brand.getDomainEvents());
        brand.clearDomainEvents();
        return BrandView.of(brand, version);
    }
}
