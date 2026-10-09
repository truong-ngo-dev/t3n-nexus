package vn.t3nexus.catalog.application.brand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandErrorCode;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.lib.common.domain.cqrs.CommandHandler;
import vn.t3nexus.lib.common.domain.exception.DomainException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateBrand implements CommandHandler<UpdateBrand.Command, UpdateBrand.Result> {

    private final BrandRepository brandRepository;

    @Override
    @Transactional
    public Result handle(Command command) {
        BrandId id = BrandId.of(command.id());
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new DomainException(BrandErrorCode.BRAND_NOT_FOUND));

        if (brandRepository.existsByNameExcludingId(command.name(), id)) {
            throw new DomainException(BrandErrorCode.BRAND_NAME_ALREADY_EXISTS);
        }

        brand.update(command.name());
        brandRepository.save(brand);

        log.info("[UpdateBrand] updated: brandId={}, traceId={}", command.id(), MDC.get("traceId"));

        return new Result(command.id());
    }

    public record Command(String id, String name) {}

    public record Result(String id) {}
}
