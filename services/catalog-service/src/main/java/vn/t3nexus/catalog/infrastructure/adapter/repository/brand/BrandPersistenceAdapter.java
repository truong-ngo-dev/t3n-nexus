package vn.t3nexus.catalog.infrastructure.adapter.repository.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;
import vn.t3nexus.catalog.infrastructure.persistence.brand.BrandJpaRepository;
import vn.t3nexus.catalog.infrastructure.persistence.brand.BrandMapper;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BrandPersistenceAdapter implements BrandRepository {

    private final BrandJpaRepository jpaRepository;

    @Override
    public Optional<Brand> findById(BrandId id) {
        return jpaRepository.findById(id.getValue())
                .map(BrandMapper::toDomain);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return jpaRepository.existsBySlug(slug);
    }

    @Override
    public List<Brand> findAllByStatus(BrandStatus status) {
        return jpaRepository.findAllByStatus(status).stream()
                .map(BrandMapper::toDomain)
                .toList();
    }

    @Override
    public void save(Brand brand) {
        jpaRepository.save(BrandMapper.toJpaEntity(brand));
    }

    @Override
    public void delete(BrandId id) {
        jpaRepository.deleteById(id.getValue());
    }
}
