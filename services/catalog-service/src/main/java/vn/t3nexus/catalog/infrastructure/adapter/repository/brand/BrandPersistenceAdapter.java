package vn.t3nexus.catalog.infrastructure.adapter.repository.brand;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    public boolean existsByNameKey(String nameKey) {
        return jpaRepository.existsByNameKey(nameKey);
    }

    @Override
    public boolean existsByNameKeyExcludingId(String nameKey, BrandId excludingId) {
        return jpaRepository.existsByNameKeyAndIdNot(nameKey, excludingId.getValue());
    }

    @Override
    public List<Brand> search(String keywordKey, BrandStatus status, int page, int size) {
        return jpaRepository.search(keywordKey, status, PageRequest.of(page, size)).stream()
                .map(BrandMapper::toDomain)
                .toList();
    }

    @Override
    public long count(String keywordKey, BrandStatus status) {
        return jpaRepository.countSearch(keywordKey, status);
    }

    /**
     * {@code saveAndFlush}: khóa lạc quan qua {@code version} và ràng buộc duy nhất nổ ngay trong giao dịch, trước khi
     * handler kiểm toán chạy (ADR-0011).
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(Brand brand) {
        jpaRepository.saveAndFlush(BrandMapper.toJpaEntity(brand));
    }
}
