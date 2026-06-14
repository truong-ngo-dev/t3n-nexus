package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;
import vn.t3nexus.lib.common.domain.service.ULIDGenerator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateBrand implements CommandHandler<CreateBrand.Command, CreateBrand.Result> {

    private final BrandRepository brandRepository;
    private final ULIDGenerator ulidGenerator;

    @Override
    @Transactional
    @CacheEvict(value = "brands:active", allEntries = true)
    public Result handle(Command command) {
        if (brandRepository.existsBySlug(command.slug())) {
            throw new DomainException(BrandErrorCode.BRAND_SLUG_ALREADY_EXISTS);
        }

        BrandId id = BrandId.of(ulidGenerator.generate());
        Brand brand = Brand.create(id, command.name(), command.slug());
        brandRepository.save(brand);

        log.info("[CreateBrand] created: brandId={}, slug={}, traceId={}",
                id.getValue(), command.slug(), MDC.get("traceId"));

        return new Result(id.getValue());
    }

    public record Command(String name, String slug) {}

    public record Result(String id) {}
}
