package vn.t3nexus.catalog.application.brand;

import vn.t3nexus.catalog.domain.brand.Brand;
import vn.t3nexus.catalog.domain.brand.BrandId;
import vn.t3nexus.catalog.domain.brand.BrandRepository;
import vn.t3nexus.catalog.domain.brand.BrandStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Kho trong bộ nhớ cho test use case. Lưu bản sao có phiên bản tăng như cơ sở dữ liệu thật (0 khi lưu lần đầu). */
public class FakeBrandRepository implements BrandRepository {

    private final Map<UUID, Brand> store = new LinkedHashMap<>();
    public int saveCount;

    @Override
    public Optional<Brand> findById(BrandId id) {
        return Optional.ofNullable(store.get(id.getValue()));
    }

    @Override
    public boolean existsByNameKey(String nameKey) {
        return store.values().stream().anyMatch(b -> b.getNameKey().equals(nameKey));
    }

    @Override
    public boolean existsByNameKeyExcludingId(String nameKey, BrandId excludingId) {
        return store.values().stream()
                .anyMatch(b -> b.getNameKey().equals(nameKey) && !b.getId().equals(excludingId));
    }

    @Override
    public List<Brand> search(String keywordKey, BrandStatus status, int page, int size) {
        return matching(keywordKey, status).stream().skip((long) page * size).limit(size).toList();
    }

    @Override
    public long count(String keywordKey, BrandStatus status) {
        return matching(keywordKey, status).size();
    }

    @Override
    public void save(Brand b) {
        saveCount++;
        store.put(b.getId().getValue(), Brand.reconstitute(b.getId(), b.getName(), b.getNameKey(), b.getStatus(),
                b.nextVersion(), b.getCreatedAt(), b.getUpdatedAt()));
    }

    public Brand stored(UUID id) {
        return store.get(id);
    }

    private List<Brand> matching(String keywordKey, BrandStatus status) {
        return store.values().stream()
                .filter(b -> keywordKey == null || b.getNameKey().contains(keywordKey))
                .filter(b -> status == null || b.getStatus() == status)
                .toList();
    }
}
