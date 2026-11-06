package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.CatalogActor;
import vn.t3nexus.catalog.domain.ChangeContext;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.lib.common.application.EventDispatcher;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.IdGenerator;

import java.time.Clock;
import java.util.UUID;

/**
 * Đổi tên thương hiệu, áp ngay mọi sản phẩm đang dùng; làm được cả khi đã ngừng dùng. Tên của thương hiệu khác thì từ
 * chối (INV-CAT-20); đổi cách viết của chính nó (hoa thường, dấu) thì được.
 */
@Service
@RequiredArgsConstructor
public class RenameBrand implements CommandHandler<RenameBrand.Command, BrandView> {

    private final BrandRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public BrandView handle(Command command) {
        command.actor().requireAdmin();
        BrandId id = BrandId.of(command.brandId());
        Brand brand = repository.findById(id)
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));
        if (repository.existsByNameKeyExcludingId(Brand.nameKeyOf(command.name()), id)) {
            throw new DomainException(BrandErrorCode.BRAND_NAME_TAKEN);
        }

        brand.rename(command.name(), new ChangeContext(command.actor(), idGenerator.generate(), clock.instant()));
        long version = brand.nextVersion();
        repository.save(brand);

        eventDispatcher.dispatchAll(brand.getDomainEvents());
        brand.clearDomainEvents();
        return BrandView.of(brand, version);
    }

    public record Command(CatalogActor actor, UUID brandId, String name) {}
}
