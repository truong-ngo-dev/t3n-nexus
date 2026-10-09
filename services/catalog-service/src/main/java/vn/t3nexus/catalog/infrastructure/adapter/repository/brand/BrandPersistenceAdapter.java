package vn.t3nexus.catalog.infrastructure.adapter.repository.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    public boolean existsByName(String name) {
        return jpaRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public boolean existsByNameExcludingId(String name, BrandId excludingId) {
        return jpaRepository.existsByNameIgnoreCaseAndIdNot(name, excludingId.getValue());
    }

    @Override
    public List<Brand> search(String keyword, BrandStatus status, int page, int size) {
        return jpaRepository.search(keyword, status, PageRequest.of(page, size)).stream()
                .map(BrandMapper::toDomain)
                .toList();
    }

    @Override
    public long count(String keyword, BrandStatus status) {
        return jpaRepository.countSearch(keyword, status);
    }

    @Override
    public void save(Brand brand) {
        jpaRepository.save(BrandMapper.toJpaEntity(brand));
    }
}
