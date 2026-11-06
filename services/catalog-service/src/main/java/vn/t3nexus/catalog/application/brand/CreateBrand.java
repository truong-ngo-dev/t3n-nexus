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

/** Quản trị tạo thương hiệu (CAP-CAT-03). Tên trùng sau chuẩn hóa thì từ chối (INV-CAT-20). */
@Service
@RequiredArgsConstructor
public class CreateBrand implements CommandHandler<CreateBrand.Command, BrandView> {

    private final BrandRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final EventDispatcher eventDispatcher;

    @Override
    @Transactional
    public BrandView handle(Command command) {
        command.actor().requireAdmin();
        if (repository.existsByNameKey(Brand.nameKeyOf(command.name()))) {
            throw new DomainException(BrandErrorCode.BRAND_NAME_TAKEN);
        }

        ChangeContext ctx = new ChangeContext(command.actor(), idGenerator.generate(), clock.instant());
        Brand brand = Brand.create(BrandId.of(idGenerator.generate()), command.name(), ctx);
        long version = brand.nextVersion();
        repository.save(brand);

        eventDispatcher.dispatchAll(brand.getDomainEvents());
        brand.clearDomainEvents();
        return BrandView.of(brand, version);
    }

    public record Command(CatalogActor actor, String name) {}
}
