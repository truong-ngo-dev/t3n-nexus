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
import vn.t3nexus.catalog.infrastructure.crosscutting.cache.CacheNames;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateBrand implements CommandHandler<UpdateBrand.Command, UpdateBrand.Result> {

    private final BrandRepository brandRepository;

    @Override
    @Transactional
    @CacheEvict(value = CacheNames.BRANDS_ACTIVE, allEntries = true)
    public Result handle(Command command) {
        Brand brand = brandRepository.findById(BrandId.of(command.id()))
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));

        brand.update(command.name());
        brandRepository.save(brand);

        log.info("[UpdateBrand] updated: brandId={}, traceId={}", command.id(), MDC.get("traceId"));

        return new Result(command.id());
    }

    public record Command(String id, String name) {}

    public record Result(String id) {}
}
